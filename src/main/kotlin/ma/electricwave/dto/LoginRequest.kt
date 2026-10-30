package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Login credentials")
data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Schema(description = "Account email", example = "client@example.com")
    val email: String = "",

    @field:NotBlank
    @field:Schema(description = "Account password", example = "s3cret-pass")
    val password: String = ""
)
