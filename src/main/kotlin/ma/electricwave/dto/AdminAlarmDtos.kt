package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "An alarm on any registered site, for administrators")
data class AdminAlarmResponse(
    @field:Schema(description = "Alarm identifier", example = "1")
    val id: Long,
    @field:Schema(description = "Access key the alarm belongs to", example = "3")
    val accessKeyId: Long,
    @field:Schema(description = "Resource identifier", example = "ctm01")
    val resource: String,
    @field:Schema(description = "What tripped", example = "OVER_POWER")
    val kind: String,
    @field:Schema(description = "FAULT for over-power, WARNING for under-voltage", example = "FAULT")
    val severity: String,
    @field:Schema(description = "Whether the alarm is still open", example = "true")
    val active: Boolean,
    @field:Schema(description = "When the alarm was raised (UTC)")
    val raisedAt: Instant,
    @field:Schema(description = "When the alarm cleared, or null while open", nullable = true)
    val clearedAt: Instant?,
    @field:Schema(description = "Reading that tripped the alarm", example = "258027.0")
    val value: Double,
    @field:Schema(description = "Limit that was crossed", example = "142000.0")
    val threshold: Double
)

@Schema(description = "Fleet-wide alarm counts and monitor status")
data class AdminAlarmSummaryResponse(
    @field:Schema(description = "Open alarms", example = "2")
    val open: Long,
    @field:Schema(description = "Open over-power alarms", example = "1")
    val overPower: Long,
    @field:Schema(description = "Open under-voltage alarms", example = "1")
    val underVoltage: Long,
    @field:Schema(description = "All recorded alarms, open and cleared", example = "40")
    val total: Long,
    @field:Schema(description = "When the last sweep finished, or null if none has run", nullable = true)
    val lastSweepAt: Instant?,
    @field:Schema(description = "Whether this instance is polling sites at all", example = "true")
    val monitoringEnabled: Boolean
)

@Schema(description = "Effective alarm limits for one registered site")
data class AdminAlarmThresholdResponse(
    @field:Schema(description = "Access key identifier", example = "3")
    val accessKeyId: Long,
    @field:Schema(description = "Resource identifier", example = "ctm01")
    val resource: String,
    @field:Schema(description = "Whether the site is still monitored", example = "true")
    val isActive: Boolean,
    @field:Schema(description = "Over-power limit in watts; null disables the check", nullable = true)
    val maxPower: Double?,
    @field:Schema(description = "Under-voltage limit in volts; null disables the check", nullable = true)
    val minVoltage: Double?,
    @field:Schema(
        description = "True when no site-specific row exists and the application defaults apply",
        example = "true"
    )
    val usingDefaults: Boolean
)
