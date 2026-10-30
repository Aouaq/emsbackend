package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Payload for requesting a new verification code")
data class ResendVerificationRequest(
    @field:NotBlank
    @field:Email
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String = ""
)
