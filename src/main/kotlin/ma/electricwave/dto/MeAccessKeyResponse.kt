package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "An Emoncms account linked to the authenticated client")
data class MeAccessKeyResponse(
    @field:Schema(description = "Access key identifier", example = "1")
    val id: Long,
    @field:Schema(description = "Resource identifier", example = "ctm01")
    val resource: String,
    @field:Schema(description = "Optional description", nullable = true)
    val description: String?,
    @field:Schema(description = "First characters of the Emoncms key", example = "3ddd\u2026")
    val keyHint: String,
    @field:Schema(description = "Whether the account is active", example = "true")
    val isActive: Boolean,
    @field:Schema(
        description = "The Emoncms read key for this account. Returned because the client " +
            "reads Emoncms directly; only ever sent to a user the account is linked to.",
        nullable = true
    )
    val emoncmsKey: String? = null,
    @field:Schema(description = "When the link was created (UTC)")
    val linkedAt: Instant
)
