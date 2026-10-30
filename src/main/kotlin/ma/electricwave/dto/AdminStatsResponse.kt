package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin dashboard statistics")
data class AdminStatsResponse(
    @field:Schema(description = "User totals")
    val users: UserStats,
    @field:Schema(description = "Access-key totals")
    val accessKeys: AccessKeyStats,
    @field:Schema(description = "Number of client-to-key links", example = "12")
    val links: Long
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
}
