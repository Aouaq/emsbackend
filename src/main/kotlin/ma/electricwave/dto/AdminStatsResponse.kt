package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin dashboard statistics")
data class AdminStatsResponse(
    @field:Schema(description = "User totals")
    val users: UserStats,
    @field:Schema(description = "Access-key totals")
    val accessKeys: AccessKeyStats,
    @field:Schema(description = "Number of client-to-key links", example = "12")
    val links: Long,
    @field:Schema(description = "Alarm totals")
    val alarms: AlarmStats
) {
    @Schema(description = "User counts")
    data class UserStats(
        @field:Schema(description = "All users", example = "20")
        val total: Long,
        @field:Schema(description = "CLIENT accounts", example = "18")
        val clients: Long,
        @field:Schema(description = "ADMIN accounts", example = "2")
        val admins: Long,
        @field:Schema(description = "Verified accounts", example = "19")
        val verified: Long,
        @field:Schema(description = "Disabled accounts", example = "1")
        val disabled: Long
    )

    @Schema(description = "Access-key counts")
    data class AccessKeyStats(
        @field:Schema(description = "All registered accounts", example = "10")
        val total: Long,
        @field:Schema(description = "Active accounts", example = "9")
        val active: Long,
        @field:Schema(description = "Inactive accounts", example = "1")
        val inactive: Long
    )

    @Schema(description = "Alarm counts")
    data class AlarmStats(
        @field:Schema(description = "Open alarms", example = "2")
        val open: Long,
        @field:Schema(description = "Open over-power alarms", example = "1")
        val overPower: Long,
        @field:Schema(description = "Open under-voltage alarms", example = "1")
        val underVoltage: Long,
        @field:Schema(description = "All recorded alarms, open and cleared", example = "40")
        val total: Long
    )
}
