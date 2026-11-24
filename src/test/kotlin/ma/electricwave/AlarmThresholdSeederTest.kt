package ma.electricwave

import ma.electricwave.config.AlarmThresholdSeeder
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.repository.AlarmThresholdRepository
import ma.electricwave.repository.AlarmThresholdSeedRepository
import ma.electricwave.service.AdminAlarmService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seeder must have its say about a site exactly once.
 *
 * Before this, the absence of a threshold row was its only signal - and a deliberate
 * reset produces exactly that state, so an operator who cleared a site back to the fleet
 * defaults found the configured limits reinstated on the next restart. These tests drive
 * the runner directly, which is what a restart does, so a regression shows up here rather
 * than on somebody's fleet at boot time.
 */
@MobileApiIntegrationTest
@TestPropertySource(
    properties = [
        "app.seed.alarm-thresholds[0].resource=seedstick",
        "app.seed.alarm-thresholds[0].max-power=4321.0",
        "app.seed.alarm-thresholds[0].min-voltage=199.0",
        "app.seed.alarm-thresholds[1].resource=seedadopt",
        "app.seed.alarm-thresholds[1].max-power=4321.0",
        "app.seed.alarm-thresholds[1].min-voltage=199.0"
    ]
)
class AlarmThresholdSeederTest : ApiTestSupport() {

    @Autowired
    private lateinit var seeder: AlarmThresholdSeeder

    @Autowired
    private lateinit var thresholds: AlarmThresholdRepository

    @Autowired
    private lateinit var seeds: AlarmThresholdSeedRepository

    @Autowired
    private lateinit var adminAlarmService: AdminAlarmService

    /** What a restart does. */
    private fun restart() = seeder.run(DefaultApplicationArguments())

    @Test
    fun `seeds a site once, and a reset then survives every later restart`() {
        val keyId = createAccessKey("seedstick", uniqueKey())

        restart()
        assertEquals(
            199.0,
            thresholds.findById(keyId).orElseThrow().minVoltage,
            "the first restart should apply the configured limits"
        )
        assertTrue(seeds.existsById(keyId), "and record that it has done so")

        // An operator clears the site back to the fleet defaults.
        adminAlarmService.resetThresholds(keyId, actorEmail = "test")
        assertTrue(thresholds.findById(keyId).isEmpty)

        // The behaviour this whole change exists for: no number of restarts brings the
        // configured limits back, because the marker outlives the row.
        restart()
        restart()
        assertTrue(
            thresholds.findById(keyId).isEmpty,
            "a deliberate reset must not be undone by restarting"
        )
    }

    @Test
    fun `a site with limits but no marker is adopted, never overwritten`() {
        val keyId = createAccessKey("seedadopt", uniqueKey())

        // Limits set by hand before markers existed: the state every current deployment
        // is in on the first boot after this change.
        adminAlarmService.updateThresholds(
            keyId,
            AlarmThresholdRequest(maxPower = 111.0, minVoltage = 222.0),
            actorEmail = "test"
        )
        assertTrue(seeds.findById(keyId).isEmpty, "no marker yet, by construction")

        restart()

        assertEquals(
            222.0,
            thresholds.findById(keyId).orElseThrow().minVoltage,
            "adoption must not move the operator's value to the configured one"
        )
        assertTrue(seeds.existsById(keyId), "but the site is now spoken for")

        // And from then on a reset sticks, same as any other site.
        adminAlarmService.resetThresholds(keyId, actorEmail = "test")
        restart()
        assertTrue(thresholds.findById(keyId).isEmpty)
    }

    private fun uniqueKey(): String = UUID.randomUUID().toString().replace("-", "")
}
