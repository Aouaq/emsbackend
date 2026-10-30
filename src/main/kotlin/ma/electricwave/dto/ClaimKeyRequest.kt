package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Claim an Emoncms account with the read key from its QR code")
data class ClaimKeyRequest(
    @field:NotBlank
    @field:Size(max = 128)
    @field:Schema(description = "Emoncms read key; 16 to 64 letters or digits", example = "3ddd9a580253f6c9aab6298f754cf0fd")
    val key: String = ""
)
