package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Payload for native Google sign-in using a Google ID token")
data class GoogleLoginRequest(
    @field:NotBlank
    @field:Schema(description = "Google ID token obtained by the mobile app")
    val idToken: String = "",

    @field:Schema(
        description = "Optional access keys to link when the account is created or first linked",
        example = "[\"ew_metering_01\"]"
    )
    val accessKeys: List<String> = emptyList()
)
