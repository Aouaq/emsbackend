package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Refresh token payload")
data class RefreshTokenRequest(
    @field:NotBlank
    @field:Schema(description = "Opaque refresh token returned by login, refresh or Google sign-in")
    val refreshToken: String = ""
)
