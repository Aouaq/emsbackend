package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Issued JWT and account information")
data class TokenResponse(
    @field:Schema(description = "Bearer token to send in the Authorization header")
    val token: String,
    @field:Schema(description = "Token type", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String,
    @field:Schema(description = "Account role", example = "CLIENT")
    val role: String,
    @field:Schema(description = "Opaque refresh token; rotate it via /api/auth/refresh")
    val refreshToken: String,
    @field:Schema(description = "Access token lifetime in seconds", example = "1800")
    val expiresIn: Long
)
