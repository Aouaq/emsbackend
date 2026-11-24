package ma.electricwave.service

import ma.electricwave.domain.AlarmKind
import kotlin.math.abs

/** What a reading should do to the alarm currently on record. */
enum class AlarmDecision { RAISE, CLEAR, HOLD }

/**
 * Decides whether a reading raises an alarm, clears one, or changes nothing.
 *
 * Pulled out of [SiteMonitorService] because it is the only part of monitoring that is
 * genuinely tricky, and reaching it through that class means a database, an Emoncms and
 * a scheduler to assert two comparisons.
 *
 * **The two edges are deliberately different numbers.** Clearing an alarm as soon as the
 * reading is no longer breached is the obvious implementation and it is wrong: a site
 * sitting *at* its limit crosses it in both directions on every sweep. That is not a
 * hypothetical - it is what the first run against the real fleet did. One site,
 * `capteur3tc01kammah`, produced **twenty-five alarm episodes in sixty-four minutes**,
 * averaging seventy-seven seconds each, because its voltage wandered between 227 and
 * 230 V against a 230 V floor. `roch01` managed eight on a 2 V swing. Every episode
 * would have been two notifications, all night.
 *
 * So a reading breaches at the threshold but only recovers once it is clearly past it,
 * and in the band between the two the alarm stays exactly as it is. This is why
 * industrial alarm handling has had deadbands for decades.
 *
 * It is **not** a substitute for a sensible threshold. A limit set where a site normally
 * runs will still alarm permanently, and correctly so - by its own definition that site
 * is always out of bounds. The deadband stops the flapping; only tuning stops that.
 */
object AlarmLogic {

    /**
     * [deadband] is a fraction of [threshold], so it scales with the quantity: the same
     * number means 2.3 V on a 230 V floor and 1.4 kW on a 142 kW ceiling. An absolute
     * margin would have to be configured per check and per site to mean anything.
     */
    fun decide(
        kind: AlarmKind,
        value: Double,
        threshold: Double,
        deadband: Double,
        isOpen: Boolean
    ): AlarmDecision {
        val margin = abs(threshold) * deadband

        val breached = when (kind) {
            AlarmKind.OVER_POWER -> value > threshold
            AlarmKind.UNDER_VOLTAGE -> value < threshold
        }
        val recovered = when (kind) {
            AlarmKind.OVER_POWER -> value < threshold - margin
            AlarmKind.UNDER_VOLTAGE -> value > threshold + margin
        }

        return when {
            // Order matters only for readability; the two cannot both hold, because
            // recovery is strictly on the far side of the threshold from a breach.
            breached && !isOpen -> AlarmDecision.RAISE
            isOpen && recovered -> AlarmDecision.CLEAR
            else -> AlarmDecision.HOLD
        }
    }
}
