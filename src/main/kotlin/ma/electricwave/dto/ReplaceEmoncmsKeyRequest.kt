package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

@Schema(description = "Replace the Emoncms read key of a registered account")
data class ReplaceEmoncmsKeyRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "[A-Za-z0-9]{16,64}",
        message = "the Emoncms key must be 16 to 64 letters or digits"
    )
    @field:Schema(description = "New Emoncms read key", example = "3ddd9a580253f6c9aab6298f754cf0fd")
    val emoncmsKey: String = ""
)
