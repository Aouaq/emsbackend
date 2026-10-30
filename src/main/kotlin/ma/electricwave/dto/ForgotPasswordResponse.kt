package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Neutral response to a password reset request")
data class ForgotPasswordResponse(
    @field:Schema(description = "Neutral status message")
    val message: String,
    @field:Schema(
        description = "DEV ONLY: reset code returned when mail sending is disabled",
        nullable = true
    )
    val devResetCode: String? = null
)
