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
    @field:Schema(
        description = "The Emoncms read key. Admin-only, and returned because the client " +
            "reads Emoncms directly rather than through this service.",
        nullable = true
    )
    val emoncmsKey: String? = null,
    @field:Schema(description = "Number of clients linked to this account", example = "1")
    val linkedClients: Int,
    @field:Schema(description = "Creation timestamp (UTC)")
    val createdAt: Instant,
    @field:Schema(description = "Last update timestamp (UTC)")
    val updatedAt: Instant,

    // The **caller's own** view of this site, from site_preferences. Null unless
    // they have set something, and never another user's values: an admin and a
    // client looking at the same site each see their own. Carried here so the admin
    // listing is as complete as the client one, which is what lets the app use a
    // single mapper for both shapes.
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
