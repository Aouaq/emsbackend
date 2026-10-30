package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "Update the authenticated account profile")
data class UpdateProfileRequest(
    @field:Size(max = 120)
    @field:Schema(description = "Display name; blank clears it", nullable = true)
    val fullName: String? = null
)
