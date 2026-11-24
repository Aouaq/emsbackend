package ma.electricwave.config

import ma.electricwave.domain.AlarmThresholdSeed
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.AlarmThresholdRepository
import ma.electricwave.repository.AlarmThresholdSeedRepository
import ma.electricwave.service.AdminAlarmService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Per-site alarm limits to apply on first boot.
 *
 * Unlike the Emoncms keys beside them these are **not secrets** - they are operating
 * limits, and they belong wherever the rest of the deployment's configuration lives.
 * They are here because the fleet defaults cannot be right for every installation: the
 * same 230 V floor that is sensible on one site sits in the middle of another's normal
 * operating range, and a limit set where a site normally runs produces an alarm that is
 * permanently on and therefore permanently ignored.
 *
 * A null limit means **that check is disabled**, exactly as it does through the admin
 * API. It does not mean "use the default" - only the absence of a row means that.
 */
@ConfigurationProperties(prefix = "app.seed")
data class AlarmThresholdSeedProperties(
    val alarmThresholds: List<SeedAlarmThreshold> = emptyList()
) {
    data class SeedAlarmThreshold(
        /** Lowercase slug, matching the Emoncms account name. */
        val resource: String = "",
        val maxPower: Double? = null,
        val minVoltage: Double? = null
    )
}

/**
 * Applies the configured limits to each site **once, ever**.
 *
 * Once is the whole point. "Has no threshold row" used to be the only signal available,
 * and a deliberate reset produces exactly that state - so an operator who cleared a site
 * back to the fleet defaults through the admin page got the configured per-site limits
 * reinstated on the next restart, with nothing to say why. The file was meant to be a
 * starting point and behaved like a policy that reasserts itself.
 *
 * So what stops this seeder is an [AlarmThresholdSeed] marker, not the presence of
 * limits. After the file has had its say about a site, whatever the limits are later -
 * tuned, reset, untouched - is somebody's decision and not this seeder's business.
 *
 * A site that already has limits but no marker predates this rule. It is adopted:
 * marked as spoken for and left exactly as it is, so the first boot after this change
 * takes the fleet as it stands rather than reasserting the file over it.
 *
 * Ordered after [AccessKeySeeder] because a threshold is keyed to an access key, and on
 * a fresh database that key is created by the run immediately before this one.
 *
 * Delegating to [AdminAlarmService.updateThresholds] keeps the 404-on-unknown-site check
 * and the audit record in one place, so a seeded limit is indistinguishable from one set
 * through the API - including in the audit log, where it appears with actor `seed`.
 */
@Component
@Order(100)
@EnableConfigurationProperties(AlarmThresholdSeedProperties::class)
class AlarmThresholdSeeder(
    private val adminAlarmService: AdminAlarmService,
    private val accessKeys: AccessKeyRepository,
    private val thresholds: AlarmThresholdRepository,
    private val seeds: AlarmThresholdSeedRepository,
    private val properties: AlarmThresholdSeedProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AlarmThresholdSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        if (properties.alarmThresholds.isEmpty()) return

        var applied = 0
        var kept = 0
        var adopted = 0

        for (seed in properties.alarmThresholds) {
            val resource = seed.resource.trim()
            if (resource.isEmpty()) {
                log.warn("Skipping an app.seed.alarm-thresholds entry with a blank resource")
                continue
            }
            if (seed.maxPower == null && seed.minVoltage == null) {
                // Both null would disable every check for the site, which is almost
                // certainly a typo rather than an intent to stop watching it.
                log.warn("Skipping '{}': both limits are null, which disables all checks", resource)
                continue
            }

            val key = accessKeys.findByResourceIgnoreCase(resource).orElse(null)
            if (key == null) {
                log.warn("Skipping alarm thresholds for unknown resource '{}'", resource)
                continue
            }

            if (seeds.existsById(key.id)) {
                // The file has already had its say about this site. Whatever the limits
                // are now - including deliberately none - is somebody's decision.
                kept++
                continue
            }

            if (thresholds.existsById(key.id)) {
                // Limits set before markers existed, by hand or by an earlier seed.
                // Record that the site is spoken for and change nothing, so this change
                // adopts the fleet as it stands instead of reasserting the file over it.
                seeds.save(AlarmThresholdSeed(accessKeyId = key.id))
                adopted++
                continue
            }

            try {
                adminAlarmService.updateThresholds(
                    key.id,
                    AlarmThresholdRequest(
                        maxPower = seed.maxPower,
                        minVoltage = seed.minVoltage
                    ),
                    actorEmail = "seed"
                )
                // Only after the limits are actually stored: a marker written first
                // would, if the write below failed, leave the site permanently unseeded
                // and permanently unexplained.
                seeds.save(AlarmThresholdSeed(accessKeyId = key.id))
                applied++
            } catch (ex: ApiException) {
                // One bad entry must not stop the rest, and must not stop the
                // application: limits are configuration, not a precondition for serving.
                log.warn("Seeding thresholds for '{}' failed: {}", resource, ex.message)
            }
        }

        if (applied > 0 || kept > 0 || adopted > 0) {
            log.info(
                "Alarm threshold seeding complete: {} applied, {} already seeded, {} adopted",
                applied,
                kept,
                adopted
            )
        }
    }
}
