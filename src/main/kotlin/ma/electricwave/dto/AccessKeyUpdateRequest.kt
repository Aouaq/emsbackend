package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Update a registered Emoncms account without replacing its key")
data class AccessKeyUpdateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    @field:Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "resource must be a lowercase slug")
    @field:Schema(description = "Resource identifier", example = "ctm01")
    val resource: String = "",

    @field:Size(max = 255)
    @field:Schema(description = "Optional description", nullable = true)
    val description: String? = null,

    @field:Schema(description = "Whether the account can be claimed and queried", example = "true")
    val isActive: Boolean = true
)
