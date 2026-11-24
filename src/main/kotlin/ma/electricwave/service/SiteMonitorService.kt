package ma.electricwave.service

import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.Alarm
import ma.electricwave.domain.AlarmKind
import ma.electricwave.repository.AlarmRepository
import ma.electricwave.repository.AlarmThresholdRepository
import ma.electricwave.repository.AccessKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Something crossed a limit, or came back. Published so the stream can stay ignorant of polling. */
data class AlarmChangedEvent(
    val alarm: Alarm,
    val resource: String,
    val raised: Boolean
)

/**
 * Watches every registered site and records crossings.
 *
 * This is the piece that makes monitoring independent of anyone looking. The app can only
 * evaluate while it is open, which is no use at three in the morning; here the check runs
 * on a timer against readings the server fetches itself.
 *
 * It is also the reason the backend reaches Emoncms again after the gateway was removed.
 * That removal was right - the gateway proxied reads the app could make for itself. This
 * is different in kind: a server cannot watch data it is not allowed to see.
 *
 * **Requires the instance to be awake.** On a host that sleeps when idle, a scheduled
 * sweep simply does not happen, and the symptom is an alarm list that stays empty and
 * looks like good news. Keep something pinging the health endpoint.
 */
@Service
@ConditionalOnProperty(
    name = ["app.monitor.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class SiteMonitorService(
    private val accessKeys: AccessKeyRepository,
    private val alarms: AlarmRepository,
    private val thresholds: AlarmThresholdRepository,
    private val crypto: CryptoService,
    private val events: ApplicationEventPublisher,
    @Value("\${app.monitor.emoncms-base-url}") private val emoncmsBaseUrl: String,
    @Value("\${app.monitor.default-max-power:142000}") private val defaultMaxPower: Double,
    @Value("\${app.monitor.default-min-voltage:230}") private val defaultMinVoltage: Double,
    @Value("\${app.monitor.stale-after:PT15M}") private val staleAfter: Duration,
    @Value("\${app.monitor.clear-deadband:0.01}") private val clearDeadband: Double,
    @Value("\${app.monitor.concurrency:4}") private val concurrency: Int
) {

    private val log = LoggerFactory.getLogger(SiteMonitorService::class.java)

    /**
     * The same client shape the mail transport uses: the JDK factory rather than
     * `SimpleClientHttpRequestFactory`, which wraps HttpURLConnection and discards the
     * response body on a 401 - exactly the failure a wrong Emoncms key produces.
     */
    private val client: RestClient = RestClient.builder()
        .baseUrl(emoncmsBaseUrl.trimEnd('/'))
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
            ).apply { setReadTimeout(Duration.ofSeconds(15)) }
        )
        .build()

    /**
     * When the last sweep finished, so a client can tell "quiet" from "not running".
     *
     * A backing field rather than `private set`: the kotlin-spring plugin opens every
     * member of a `@Service`, and an open property may not have a private setter.
     */
    @Volatile
    private var lastSweep: Instant? = null

    val lastSweepAt: Instant? get() = lastSweep

    @Scheduled(fixedDelayString = "\${app.monitor.interval:PT30S}")
    fun sweep() {
        val sites = accessKeys.findAllByIsActiveTrueOrderByResource()
        if (sites.isEmpty()) {
            lastSweep = Instant.now()
            return
        }

        // Bounded concurrency. Twelve sequential round trips do not fit in a thirty
        // second tick, and an unbounded pool would open twelve sockets at once on a
        // free-tier instance that has little else to give.
        val pool = Executors.newFixedThreadPool(concurrency.coerceIn(1, 8))
        try {
            val work = sites.map { site -> Callable { checkSite(site) } }
            pool.invokeAll(work, 25, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            pool.shutdownNow()
        }
        lastSweep = Instant.now()
    }

    private fun checkSite(site: AccessKey) {
        val reading = try {
            read(site)
        } catch (ex: Exception) {
            // One unreachable site must not end the sweep for the other eleven.
            log.warn("Monitor: could not read {}: {}", site.resource, ex.message)
            return
        } ?: return

        // Only the absence of a row falls back to the defaults. A stored row with a null
        // limit is a check the administrator deliberately switched off, and treating that
        // as "use the default" would make the switch do nothing.
        val limits = thresholds.findById(site.id).orElse(null)
        val maxPower = if (limits == null) defaultMaxPower else limits.maxPower
        val minVoltage = if (limits == null) defaultMinVoltage else limits.minVoltage

        evaluate(site, AlarmKind.OVER_POWER, reading.power, maxPower)
        evaluate(site, AlarmKind.UNDER_VOLTAGE, reading.voltage, minVoltage)
    }

    /**
     * Raises and clears one check, per [AlarmLogic.decide].
     *
     * The decision itself lives in [AlarmLogic] rather than here because it is the part
     * worth testing, and testing it through this method would mean standing up a
     * database, an Emoncms and a clock to assert a comparison.
     */
    private fun evaluate(
        site: AccessKey,
        kind: AlarmKind,
        value: Double?,
        threshold: Double?
    ) {
        // A check with no limit, or a site that does not publish the quantity, is not a
        // passing check - it is one that cannot be made.
        if (value == null || threshold == null) return

        val open = alarms.findFirstByAccessKeyIdAndKindAndClearedAtIsNull(site.id, kind)
        val decision = AlarmLogic.decide(
            kind = kind,
            value = value,
            threshold = threshold,
            deadband = clearDeadband,
            isOpen = open.isPresent
        )

        if (decision == AlarmDecision.RAISE) {
            val alarm = Alarm(
                accessKeyId = site.id,
                kind = kind,
                raisedAt = Instant.now(),
                value = value,
                threshold = threshold
            )
            try {
                events.publishEvent(AlarmChangedEvent(alarms.save(alarm), site.resource, raised = true))
                log.info("Alarm raised: {} {} {} (limit {})", site.resource, kind, value, threshold)
            } catch (ex: DataIntegrityViolationException) {
                // The partial unique index caught a race with an overlapping sweep.
                // The other tick recorded it; nothing to do and nothing wrong.
                log.debug("Monitor: {} {} already open", site.resource, kind)
            }
            return
        }

        if (decision == AlarmDecision.CLEAR) {
            val alarm = open.get().apply { clearedAt = Instant.now() }
            events.publishEvent(AlarmChangedEvent(alarms.save(alarm), site.resource, raised = false))
            log.info("Alarm cleared: {} {} {}", site.resource, kind, value)
        }
    }

    private data class Reading(val power: Double?, val voltage: Double?)

    /** Null when the site's data is too old to judge. */
    private fun read(site: AccessKey): Reading? {
        val apiKey = crypto.decrypt(site.emoncmsApiKeyEnc)
        val feeds = client.get()
            .uri { it.path("/feed/list.json").queryParam("apikey", apiKey).build() }
            .retrieve()
            .body(object : org.springframework.core.ParameterizedTypeReference<List<Map<String, Any?>>>() {})
            ?: return null

        val power = pick(feeds, POWER_NAMES)
        val voltage = pick(feeds, VOLTAGE_NAMES)
        val newest = listOfNotNull(power?.second, voltage?.second).maxOrNull() ?: return null

        // A site that stopped reporting keeps returning whatever it last wrote. Judging
        // that would latch an alarm on a months-old number which can never clear, because
        // the site is not sending anything that could bring it back under the limit.
        val age = Duration.between(Instant.ofEpochSecond(newest), Instant.now())
        if (age > staleAfter) return null

        return Reading(power?.first, voltage?.first)
    }

    /** The first feed matching any name, with its value and timestamp. */
    private fun pick(feeds: List<Map<String, Any?>>, names: List<String>): Pair<Double, Long>? {
        for (name in names) {
            for (feed in feeds) {
                if (feed["name"]?.toString()?.uppercase() != name) continue
                val value = feed["value"]?.toString()?.toDoubleOrNull() ?: continue
                val time = feed["time"]?.toString()?.toDoubleOrNull()?.toLong() ?: continue
                return value to time
            }
        }
        return null
    }

    private companion object {
        /**
         * These sites do not share a feed vocabulary - some name their channels in
         * French - so matching one name would silently leave those sites unwatched.
         * Mirrors the list the app uses.
         */
        val POWER_NAMES = listOf("PT", "P_TOTALE", "PUISSANCE", "POWER")
        val VOLTAGE_NAMES = listOf("VS", "TENSION", "VOLTAGE", "V")
    }
}
