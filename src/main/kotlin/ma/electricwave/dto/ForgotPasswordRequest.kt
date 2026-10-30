package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Request a password reset code")
data class ForgotPasswordRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String = ""
)
