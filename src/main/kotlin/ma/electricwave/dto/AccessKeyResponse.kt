package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Access key details, including the clients linked to it")
data class AccessKeyResponse(
    @field:Schema(description = "Database identifier", example = "1")
    val id: Long,
    @field:Schema(description = "Key value", example = "ew_metering_01")
    val keyValue: String,
    @field:Schema(description = "Resource protected by the key", example = "metering")
    val resource: String,
    @field:Schema(description = "Optional description", nullable = true)
    val description: String?,
    @field:Schema(description = "Whether the key is active", example = "true")
    val isActive: Boolean,
    @field:Schema(description = "Creation timestamp (UTC)")
    val createdAt: Instant,
    @field:Schema(description = "Emails of client users linked to this key")
    val linkedClients: List<String> = emptyList()
)
