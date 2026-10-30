package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Registered Emoncms account; never includes the Emoncms key")
data class AccessKeyResponse(
    @field:Schema(description = "Database identifier", example = "1")
    val id: Long,
    @field:Schema(description = "Resource identifier", example = "ctm01")
    val resource: String,
    @field:Schema(description = "Optional description", nullable = true)
    val description: String?,
    @field:Schema(description = "Whether the account can be claimed", example = "true")
    val isActive: Boolean,
    @field:Schema(description = "First characters of the Emoncms key", example = "3ddd")
    val keyHint: String,
    @field:Schema(description = "Whether an encrypted Emoncms key is stored", example = "true")
    val emoncmsKeyConfigured: Boolean,
    @field:Schema(description = "Number of clients linked to this account", example = "1")
    val linkedClients: Int,
    @field:Schema(description = "Creation timestamp (UTC)")
    val createdAt: Instant,
    @field:Schema(description = "Last update timestamp (UTC)")
    val updatedAt: Instant
)
