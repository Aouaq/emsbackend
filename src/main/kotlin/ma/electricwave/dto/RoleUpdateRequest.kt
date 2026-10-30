package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import ma.electricwave.domain.Role

@Schema(description = "Payload for changing a user's role")
data class RoleUpdateRequest(
    @field:NotNull
    @field:Schema(description = "New role", example = "ADMIN")
    val role: Role? = null
)
