package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ma.electricwave.domain.Role

@Schema(description = "Payload for provisioning a verified local account")
data class AdminUserCreateRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    @field:Schema(description = "Unique account email", example = "admin2@example.com")
    val email: String = "",

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(description = "Password, 8 to 72 characters", example = "s3cret-pass")
    val password: String = "",

    @field:Size(max = 255)
    @field:Schema(description = "Optional display name", nullable = true)
    val fullName: String? = null,

    @field:NotNull
    @field:Schema(description = "Role granted to the new account", example = "ADMIN")
    val role: Role? = null
)
