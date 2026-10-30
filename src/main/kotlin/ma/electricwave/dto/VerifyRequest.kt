package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Payload for verifying an email address")
data class VerifyRequest(
    @field:NotBlank
    @field:Email
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String = "",

    @field:NotBlank
    @field:Schema(description = "6-digit code received by email", example = "123456")
    val code: String = ""
)
