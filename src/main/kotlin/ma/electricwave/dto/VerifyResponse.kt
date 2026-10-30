package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of the email verification request")
data class VerifyResponse(
    @field:Schema(description = "Verified email", example = "client@example.com")
    val email: String,
    @field:Schema(description = "Verification status", example = "true")
    val verified: Boolean,
    @field:Schema(description = "Human-readable status message")
    val message: String
)
