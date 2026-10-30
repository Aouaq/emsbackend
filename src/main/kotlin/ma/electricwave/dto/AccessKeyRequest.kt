package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Payload for creating an access key")
data class AccessKeyRequest(
    @field:Size(max = 255)
    @field:Pattern(regexp = "[A-Za-z0-9_.-]*", message = "only letters, digits, '_', '.' and '-' are allowed")
    @field:Schema(
        description = "Optional explicit key value; a random ew_<hex> value is generated when omitted",
        example = "ew_billing_02",
        nullable = true
    )
    val keyValue: String? = null,

    @field:NotBlank
    @field:Size(max = 128)
    @field:Pattern(regexp = "[a-z0-9-]+", message = "resource must be lowercase kebab or snake case")
    @field:Schema(description = "Resource identifier protected by this key", example = "billing")
    val resource: String = "",

    @field:Size(max = 512)
    @field:Schema(description = "Optional description", example = "Invoices and payments", nullable = true)
    val description: String? = null,

    @field:Schema(description = "Whether the key can be used immediately", example = "true")
    val isActive: Boolean = true
)
