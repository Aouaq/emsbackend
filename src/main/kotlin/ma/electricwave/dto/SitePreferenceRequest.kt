package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size

/**
 * Edits one user's own view of a site. Every field is optional.
 *
 * **Absent and blank mean different things.** A field left out of the body is untouched,
 * so a client that only changes the name does not have to send the address back; a field
 * sent blank clears it and returns that part to the fallback. That distinction is why
 * the fields are nullable here and why the service reads the raw map rather than
 * treating null as "clear".
 */
@Schema(description = "Rename a site, or set its location or photo, for the caller only")
data class SitePreferenceRequest(
    @field:Size(max = 100)
    @field:Schema(description = "What the caller calls this site; blank clears it", nullable = true)
    val name: String? = null,

    @field:Size(max = 255)
    @field:Schema(
        description = "Picked region, e.g. Marrakech,Marrakech-Safi,Morocco",
        nullable = true
    )
    val region: String? = null,

    @field:Size(max = 255)
    @field:Schema(description = "Free-text street or locality, e.g. Targa", nullable = true)
    val addressDetail: String? = null,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    @field:Schema(description = "Map pin latitude", nullable = true)
    val latitude: Double? = null,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    @field:Schema(description = "Map pin longitude", nullable = true)
    val longitude: Double? = null,

    @field:Size(max = 512)
    @field:Schema(description = "Object-store URL of the site photo", nullable = true)
    val imageUrl: String? = null
)
