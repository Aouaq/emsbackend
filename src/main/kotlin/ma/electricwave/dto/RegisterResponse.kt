package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of the registration request")
data class RegisterResponse(
    @field:Schema(description = "Registered email", example = "client@example.com")
    val email: String,
    @field:Schema(description = "Verification status, always false after registration", example = "false")
    val verified: Boolean,
    @field:Schema(description = "Human-readable status message")
    val message: String,
    @field:Schema(
        description = "Whether the verification email was actually accepted by the mail server. " +
            "False when delivery failed or mail is disabled; call /api/auth/verify/resend to retry.",
        example = "true"
    )
    val verificationCodeSent: Boolean = true,
    @field:Schema(
        description = "DEV ONLY: verification code returned when mail sending is disabled",
        nullable = true
    )
    val devVerificationCode: String? = null
)
