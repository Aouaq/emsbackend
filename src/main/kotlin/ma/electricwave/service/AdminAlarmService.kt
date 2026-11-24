package ma.electricwave.service

import jakarta.persistence.criteria.Predicate
import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.Alarm
import ma.electricwave.domain.AlarmKind
import ma.electricwave.domain.AlarmThreshold
import ma.electricwave.dto.AdminAlarmResponse
import ma.electricwave.dto.AdminAlarmSummaryResponse
import ma.electricwave.dto.AdminAlarmThresholdResponse
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.dto.PageResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.AlarmRepository
import ma.electricwave.repository.AlarmThresholdRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Fleet-wide alarm access for administrators.
 *
 * The client-facing [AlarmService] answers for the sites one user has claimed. This one
 * answers for every registered site, including inactive ones, because an administrator
 * tunes limits per installation rather than per person and needs to see a site before it
 * is handed to a client.
 */
@Service
class AdminAlarmService(
    private val alarms: AlarmRepository,
    private val thresholds: AlarmThresholdRepository,
    private val accessKeys: AccessKeyRepository,
    private val auditService: AuditService,
    /**
     * Optional for the same reason as in [AlarmService]: tests and deployments that do
     * not watch sites run without a monitor. The list still answers; the page just says
     * that nothing is watching.
     */
    private val monitor: ObjectProvider<SiteMonitorService>,
    @Value("\${app.monitor.default-max-power:142000}") private val defaultMaxPower: Double,
    @Value("\${app.monitor.default-min-voltage:230}") private val defaultMinVoltage: Double
) {

    /**
     * Every alarm, newest first, narrowed by state, kind or resource.
     *
     * `active` is nullable on purpose: null means "both", which is the useful default
     * for an API but not for the page, where the handler chooses open-only.
     */
    @Transactional(readOnly = true)
    fun list(
        active: Boolean?,
        kind: AlarmKind?,
        resource: String?,
        page: Int,
        size: Int
    ): PageResponse<AdminAlarmResponse> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, PageResponse.MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "raisedAt")
        )

        val resourceFilter = resource?.trim()?.takeIf { it.isNotEmpty() }
        val accessKeyId = resourceFilter?.let {
            accessKeys.findByResourceIgnoreCase(it).map { key -> key.id }.orElse(null)
        }
        // A filter that names no site is an empty result, not an error: the caller asked
        // for alarms matching a resource, and none do. A 404 would make a typo look like
        // a broken endpoint.
        if (resourceFilter != null && accessKeyId == null) {
            return PageResponse(emptyList(), pageable.pageNumber, pageable.pageSize, 0, 0)
        }

        val spec = Specification<Alarm> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            when (active) {
                true -> predicates += cb.isNull(root.get<Instant>("clearedAt"))
                false -> predicates += cb.isNotNull(root.get<Instant>("clearedAt"))
                null -> Unit
            }
            kind?.let { predicates += cb.equal(root.get<AlarmKind>("kind"), it) }
            accessKeyId?.let { predicates += cb.equal(root.get<Long>("accessKeyId"), it) }
            cb.and(*predicates.toTypedArray())
        }

        val result = alarms.findAll(spec, pageable)
        val resources = resourceNames(result.content)
        return PageResponse.from(
            result.map { it.toAdminResponse(resources[it.accessKeyId] ?: "") }
        )
    }

    @Transactional(readOnly = true)
    fun summary(): AdminAlarmSummaryResponse {
        val watcher = monitor.getIfAvailable()
        return AdminAlarmSummaryResponse(
            open = alarms.countByClearedAtIsNull(),
            overPower = alarms.countByClearedAtIsNullAndKind(AlarmKind.OVER_POWER),
            underVoltage = alarms.countByClearedAtIsNullAndKind(AlarmKind.UNDER_VOLTAGE),
            total = alarms.count(),
            lastSweepAt = watcher?.lastSweepAt,
            monitoringEnabled = watcher != null
        )
    }

    /** Every registered site, including inactive ones, with its effective limits. */
    @Transactional(readOnly = true)
    fun thresholds(): List<AdminAlarmThresholdResponse> {
        val stored = thresholds.findAll().associateBy { it.accessKeyId }
        return accessKeys.findAll()
            .sortedBy { it.resource }
            .map { key -> toThresholdResponse(key, stored[key.id]) }
    }

    @Transactional
    fun updateThresholds(
        accessKeyId: Long,
        request: AlarmThresholdRequest,
        actorEmail: String?
    ): AdminAlarmThresholdResponse {
        val key = findAccessKey(accessKeyId)
        val row = thresholds.findById(accessKeyId)
            .orElseGet { AlarmThreshold(accessKeyId = accessKeyId) }
            .apply {
                maxPower = request.maxPower
                minVoltage = request.minVoltage
                updatedAt = Instant.now()
            }
        thresholds.save(row)

        auditService.record(
            "ALARM_THRESHOLDS_UPDATED",
            actorEmail = actorEmail,
            resource = key.resource,
            details = mapOf(
                "maxPower" to row.maxPower,
                "minVoltage" to row.minVoltage
            )
        )
        return toThresholdResponse(key, row)
    }

    /**
     * Removes the site-specific row so the application defaults apply again.
     *
     * Without this an administrator could disable a check but never undo the row, because
     * a null limit means "disabled" and not "default". Deleting is the only honest way
     * back to the defaults.
     */
    @Transactional
    fun resetThresholds(accessKeyId: Long, actorEmail: String?): AdminAlarmThresholdResponse {
        val key = findAccessKey(accessKeyId)
        thresholds.findById(accessKeyId).ifPresent { row ->
            thresholds.delete(row)
            auditService.record(
                "ALARM_THRESHOLDS_RESET",
                actorEmail = actorEmail,
                resource = key.resource
            )
        }
        return toThresholdResponse(key, null)
    }

    private fun findAccessKey(accessKeyId: Long): AccessKey =
        accessKeys.findById(accessKeyId).orElseThrow {
            ApiException(
                HttpStatus.NOT_FOUND,
                "Access key $accessKeyId not found",
                "NOT_FOUND"
            )
        }

    /** Access key id to resource name, in one query rather than one per alarm. */
    private fun resourceNames(rows: List<Alarm>): Map<Long, String> {
        if (rows.isEmpty()) return emptyMap()
        return accessKeys.findAllById(rows.map { it.accessKeyId }.toSet())
            .associate { it.id to it.resource }
    }

    private fun toThresholdResponse(
        key: AccessKey,
        row: AlarmThreshold?
    ): AdminAlarmThresholdResponse = AdminAlarmThresholdResponse(
        accessKeyId = key.id,
        resource = key.resource,
        isActive = key.isActive,
        // A stored row with a null limit means the check is disabled. Only the absence of
        // a row falls back to the application defaults, which is why this is not an elvis
        // on row.maxPower.
        maxPower = if (row == null) defaultMaxPower else row.maxPower,
        minVoltage = if (row == null) defaultMinVoltage else row.minVoltage,
        usingDefaults = row == null
    )

    private fun Alarm.toAdminResponse(resource: String) = AdminAlarmResponse(
        id = id,
        accessKeyId = accessKeyId,
        resource = resource,
        kind = kind.name,
        severity = AlarmService.severityOf(kind),
        active = clearedAt == null,
        raisedAt = raisedAt,
        clearedAt = clearedAt,
        value = value,
        threshold = threshold
    )
}
