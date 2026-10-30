package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Change the password of the authenticated account")
data class ChangePasswordRequest(
    @field:NotBlank
    @field:Size(max = 72)
    @field:Schema(description = "Current password")
    val currentPassword: String = "",

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(description = "New password, 8 to 72 characters")
    val newPassword: String = ""
)
