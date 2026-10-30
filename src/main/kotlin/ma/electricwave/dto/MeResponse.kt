package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Authenticated account profile")
data class MeResponse(
    @field:Schema(description = "Database identifier", example = "1")
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
    @field:Schema(description = "Last successful login (UTC)", nullable = true)
    val lastLoginAt: Instant?
)
