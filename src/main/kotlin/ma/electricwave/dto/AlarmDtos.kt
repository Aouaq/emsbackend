package ma.electricwave.dto

import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class AlarmResponse(
    val id: Long,
    val resource: String,
    val kind: String,
    val severity: String,
    val raisedAt: Instant,
    val clearedAt: Instant?,
    val value: Double,
    val threshold: Double
) {
    val active: Boolean get() = clearedAt == null
}

data class AlarmThresholdResponse(
    val resource: String,
    val maxPower: Double?,
    val minVoltage: Double?,

    /** True when no row exists and the application defaults are in force. */
    val usingDefaults: Boolean
)

/**
 * Null disables a check, which is deliberately different from zero: a ceiling of zero
 * watts is a limit every live site breaches permanently, so the two cannot share a
 * representation.
 */
data class AlarmThresholdRequest(
    @field:PositiveOrZero(message = "maxPower must not be negative")
    val maxPower: Double? = null,

    @field:PositiveOrZero(message = "minVoltage must not be negative")
    val minVoltage: Double? = null
)

/**
 * The alarm list, plus when the server last managed to look.
 *
 * [lastSweepAt] is the difference between "nothing is wrong" and "nothing is watching".
 * An empty list means the same thing in both cases, and without this a stopped poller is
 * indistinguishable from a quiet fleet - which is the failure mode most likely to go
 * unnoticed, because it looks like good news.
 */
data class AlarmFeedResponse(
    val alarms: List<AlarmResponse>,
    val lastSweepAt: Instant?,
    val monitoringEnabled: Boolean
)
