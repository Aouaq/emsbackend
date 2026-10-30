package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Payload for registering an Emoncms account")
data class AccessKeyRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "[A-Za-z0-9]{16,64}",
        message = "the Emoncms key must be 16 to 64 letters or digits"
    )
    @field:Schema(
        description = "Emoncms read API key from the account's QR code or dashboard",
        example = "3ddd9a580253f6c9aab6298f754cf0fd"
    )
    val emoncmsKey: String = "",

    @field:NotBlank
    @field:Size(max = 100)
    @field:Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "resource must be a lowercase slug")
    @field:Schema(description = "Resource identifier (Emoncms account slug)", example = "ctm01")
    val resource: String = "",

    @field:Size(max = 255)
    @field:Schema(description = "Optional description", example = "Home consumption meter", nullable = true)
    val description: String? = null,

    @field:Schema(description = "Whether the account can be claimed immediately", example = "true")
    val isActive: Boolean = true
)
