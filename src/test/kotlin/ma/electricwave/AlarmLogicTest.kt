package ma.electricwave

import ma.electricwave.domain.AlarmKind
import ma.electricwave.service.AlarmDecision
import ma.electricwave.service.AlarmLogic
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The deadband, and the flapping it exists to stop.
 *
 * The sequences below are not invented. They are the readings the first live run against
 * the real fleet produced, which is also how the bug was found: twenty-five alarm
 * episodes from one site in sixty-four minutes.
 */
class AlarmLogicTest {

    private val deadband = 0.01

    private fun decide(
        kind: AlarmKind,
        value: Double,
        threshold: Double,
        isOpen: Boolean
    ) = AlarmLogic.decide(kind, value, threshold, deadband, isOpen)

    // --- Raising ------------------------------------------------------------------

    @Test
    fun `raises when power passes the ceiling`() {
        // nfis01, measured: 258 kW against the 142 kW default.
        assertEquals(
            AlarmDecision.RAISE,
            decide(AlarmKind.OVER_POWER, 258027.0, 142000.0, isOpen = false)
        )
    }

    @Test
    fun `raises when voltage falls below the floor`() {
        // ems1817, measured.
        assertEquals(
            AlarmDecision.RAISE,
            decide(AlarmKind.UNDER_VOLTAGE, 222.1, 230.0, isOpen = false)
        )
    }

    @Test
    fun `does not raise twice for the same crossing`() {
        assertEquals(
            AlarmDecision.HOLD,
            decide(AlarmKind.OVER_POWER, 258027.0, 142000.0, isOpen = true)
        )
    }

    @Test
    fun `holds while a reading sits exactly on the threshold`() {
        // Not breached (the comparison is strict), and not recovered either.
        assertEquals(
            AlarmDecision.HOLD,
            decide(AlarmKind.UNDER_VOLTAGE, 230.0, 230.0, isOpen = false)
        )
    }

    // --- Clearing -----------------------------------------------------------------

    @Test
    fun `clears only once the reading is clearly past the limit`() {
        // 230 V floor, 1% deadband: the clear line is 232.3 V.
        assertEquals(
            AlarmDecision.HOLD,
            decide(AlarmKind.UNDER_VOLTAGE, 231.0, 230.0, isOpen = true),
        )
        assertEquals(
            AlarmDecision.CLEAR,
            decide(AlarmKind.UNDER_VOLTAGE, 233.0, 230.0, isOpen = true),
        )
    }

    @Test
    fun `clears power only once it is clearly back under the ceiling`() {
        // 142 kW ceiling, 1% deadband: the clear line is 140.58 kW.
        assertEquals(
            AlarmDecision.HOLD,
            decide(AlarmKind.OVER_POWER, 141000.0, 142000.0, isOpen = true)
        )
        assertEquals(
            AlarmDecision.CLEAR,
            decide(AlarmKind.OVER_POWER, 139000.0, 142000.0, isOpen = true)
        )
    }

    // --- The regression -----------------------------------------------------------

    @Test
    fun `a site hovering at its limit raises once and stays raised`() {
        // capteur3tc01kammah, consecutive sweeps as recorded. Before the deadband this
        // produced an alarm on every reading below 230 and a clear on every one above:
        // twenty-five episodes in an hour, fifty notifications.
        val readings = listOf(
            229.21, 229.62, 227.43, 229.83, 229.71, 229.96,
            229.60, 228.41, 229.37, 229.68, 227.38, 224.16,
            224.92, 228.13, 228.61, 225.63, 227.02, 229.78,
        )

        var open = false
        var raises = 0
        var clears = 0
        for (reading in readings) {
            when (decide(AlarmKind.UNDER_VOLTAGE, reading, 230.0, open)) {
                AlarmDecision.RAISE -> { raises++; open = true }
                AlarmDecision.CLEAR -> { clears++; open = false }
                AlarmDecision.HOLD -> Unit
            }
        }

        assertEquals(1, raises, "should raise once, not once per reading")
        assertEquals(0, clears, "nothing here recovered past 232.3 V")
        assertEquals(true, open)
    }

    @Test
    fun `a genuine recovery still clears`() {
        // The deadband must not make an alarm permanent. A site that actually comes back
        // to nominal has to clear, or the next real fault is indistinguishable from the
        // last one that was never resolved.
        var open = true
        var clears = 0
        for (reading in listOf(228.0, 229.5, 231.0, 234.0, 235.1)) {
            if (decide(AlarmKind.UNDER_VOLTAGE, reading, 230.0, open) == AlarmDecision.CLEAR) {
                clears++
                open = false
            }
        }
        assertEquals(1, clears)
        assertEquals(false, open)
    }

    @Test
    fun `a deadband of zero is the old flapping behaviour`() {
        // Kept as documentation of what the configuration knob does, and so that setting
        // it to zero is a deliberate choice rather than a surprise.
        assertEquals(
            AlarmDecision.CLEAR,
            AlarmLogic.decide(
                AlarmKind.UNDER_VOLTAGE, 230.01, 230.0, deadband = 0.0, isOpen = true
            )
        )
    }
}
