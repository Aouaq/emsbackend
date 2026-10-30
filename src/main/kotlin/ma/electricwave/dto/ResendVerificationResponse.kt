package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of a resend-verification request")
data class ResendVerificationResponse(
    @field:Schema(description = "Neutral status message")
    val message: String,
    @field:Schema(
        description = "DEV ONLY: new verification code returned when mail sending is disabled",
        nullable = true
    )
    val devVerificationCode: String? = null
)
