package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "User account details for admin management")
data class AdminUserResponse(
    @field:Schema(description = "Database identifier", example = "2")
    val id: Long,
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String,
    @field:Schema(description = "Display name", nullable = true)
    val fullName: String?,
    @field:Schema(description = "Account role", example = "CLIENT")
    val role: String,
    @field:Schema(description = "Authentication provider", example = "LOCAL")
    val provider: String,
    @field:Schema(description = "Whether the email is verified", example = "true")
    val verified: Boolean,
    @field:Schema(description = "Creation timestamp (UTC)")
    val createdAt: Instant,
    @field:Schema(description = "Values of the access keys linked to this user")
    val accessKeys: List<String> = emptyList()
)
