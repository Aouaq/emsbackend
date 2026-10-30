package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Payload for creating a client account")
data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    @field:Schema(description = "Unique account email", example = "client@example.com")
    val email: String = "",

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(description = "Password, 8 to 72 characters", example = "s3cret-pass")
    val password: String = "",

    @field:Size(max = 255)
    @field:Schema(description = "Optional display name", example = "Client One", nullable = true)
    val fullName: String? = null,

    @field:Schema(
        description = "Access keys to link to the account; every key must exist and be active",
        example = "[\"ew_metering_01\"]"
    )
    val accessKeys: List<String> = emptyList()
)
