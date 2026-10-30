package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Reset a forgotten password with an emailed code")
data class ResetPasswordRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String = "",

    @field:NotBlank
    @field:Size(min = 6, max = 6)
    @field:Schema(description = "6-digit reset code", example = "123456")
    val code: String = "",

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(description = "New password, 8 to 72 characters")
    val newPassword: String = ""
)
