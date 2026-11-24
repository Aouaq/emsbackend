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
    val linkedAt: Instant,

    // The caller's own view of this site, from site_preferences. Null throughout
    // where they have set nothing, which is the normal case - the client then falls
    // back to description and finally to resource, exactly as it did before these
    // fields existed. Carried on this response rather than fetched separately so a
    // sync stays one request.
    @field:Schema(description = "What the caller calls this site", nullable = true)
    val name: String? = null,
    @field:Schema(description = "Picked region, e.g. Marrakech,Marrakech-Safi,Morocco", nullable = true)
    val region: String? = null,
    @field:Schema(description = "Free-text street or locality, e.g. Targa", nullable = true)
    val addressDetail: String? = null,
    @field:Schema(description = "Map pin latitude", nullable = true)
    val latitude: Double? = null,
    @field:Schema(description = "Map pin longitude", nullable = true)
    val longitude: Double? = null,
    @field:Schema(description = "Object-store URL of the site photo", nullable = true)
    val imageUrl: String? = null
)
