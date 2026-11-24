package ma.electricwave.service

import ma.electricwave.domain.Alarm
import ma.electricwave.domain.AlarmKind
import ma.electricwave.domain.AlarmThreshold
import ma.electricwave.dto.AlarmFeedResponse
import ma.electricwave.dto.AlarmResponse
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.dto.AlarmThresholdResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.AlarmRepository
import ma.electricwave.repository.AlarmThresholdRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AlarmService(
    private val alarms: AlarmRepository,
    private val thresholds: AlarmThresholdRepository,
    private val accessKeys: AccessKeyRepository,
    /**
     * Optional: the monitor is absent when `app.monitor.enabled=false`, which is how the
     * test suite and any non-watching deployment run. The feed still answers in that
     * case - it just reports that nothing is watching, which is the honest answer.
     */
    private val monitor: ObjectProvider<SiteMonitorService>,
    @Value("\${app.monitor.default-max-power:142000}") private val defaultMaxPower: Double,
    @Value("\${app.monitor.default-min-voltage:230}") private val defaultMinVoltage: Double
) {

    /** Alarms for the sites this user has claimed, newest first. */
    fun feed(userId: Long, limit: Int = 200): AlarmFeedResponse {
        val rows = alarms.findForUser(userId, limit.coerceIn(1, 500))
        val resources = resourceNames(rows)
        val watcher = monitor.getIfAvailable()

        return AlarmFeedResponse(
            alarms = rows.map { it.toResponse(resources[it.accessKeyId] ?: "") },
            lastSweepAt = watcher?.lastSweepAt,
            monitoringEnabled = watcher != null
        )
    }

    fun thresholdsFor(userId: Long): List<AlarmThresholdResponse> =
        accessKeys.findLinkedByUserId(userId).map { link ->
            val stored = thresholds.findById(link.getAccessKeyId()).orElse(null)
            AlarmThresholdResponse(
                resource = link.getResource(),
                // A stored null disables that check; only a missing row uses the defaults.
                maxPower = if (stored == null) defaultMaxPower else stored.maxPower,
                minVoltage = if (stored == null) defaultMinVoltage else stored.minVoltage,
                usingDefaults = stored == null
            )
        }

    @Transactional
    fun setThresholds(
        userId: Long,
        resource: String,
        request: AlarmThresholdRequest
    ): AlarmThresholdResponse {
        // Only sites the caller has claimed. Resolved from their own links rather than
        // by looking the resource up globally, so an unlinked resource is simply not
        // found - the response cannot be used to discover which sites exist.
        val link = accessKeys.findLinkedByUserId(userId)
            .firstOrNull { it.getResource().equals(resource, ignoreCase = true) }
            ?: throw ApiException(HttpStatus.NOT_FOUND, "No such account", "NOT_FOUND")

        val row = thresholds.findById(link.getAccessKeyId())
            .orElseGet { AlarmThreshold(accessKeyId = link.getAccessKeyId()) }
            .apply {
                maxPower = request.maxPower
                minVoltage = request.minVoltage
                updatedAt = Instant.now()
            }
        thresholds.save(row)

        return AlarmThresholdResponse(
            resource = link.getResource(),
            maxPower = row.maxPower,
            minVoltage = row.minVoltage,
            usingDefaults = false
        )
    }

    /** Access key id to resource name, in one query rather than one per alarm. */
    private fun resourceNames(rows: List<Alarm>): Map<Long, String> {
        if (rows.isEmpty()) return emptyMap()
        val ids = rows.map { it.accessKeyId }.toSet()
        return accessKeys.findAllById(ids).associate { it.id to it.resource }
    }

    private fun Alarm.toResponse(resource: String) = AlarmResponse(
        id = id,
        resource = resource,
        kind = kind.name,
        severity = severityOf(kind),
        raisedAt = raisedAt,
        clearedAt = clearedAt,
        value = value,
        threshold = threshold
    )

    companion object {
        /**
         * Over-power is a fault; under-voltage is a warning.
         *
         * Drawing more than an installation is rated for risks the installation, while a
         * sagging supply is usually the grid's doing and outside anyone's control here.
         */
        fun severityOf(kind: AlarmKind): String =
            if (kind == AlarmKind.OVER_POWER) "FAULT" else "WARNING"
    }
}
