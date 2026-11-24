package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One user's own name, location and photo for one site.
 *
 * Deliberately **not** on [AccessKey]: a key is shared by every user linked to it, and
 * these fields are personal. Putting a name on the key would mean one person's rename
 * silently relabelling the site for everyone else looking at it.
 *
 * Every field is nullable and an absent row is the normal case. A reader falls back to
 * the key's description and then to its resource, which is what the app displayed
 * before this existed.
 */
@Entity
@Table(name = "site_preferences")
class SitePreference(
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "access_key_id", nullable = false)
    var accessKeyId: Long = 0,

    /** What this user calls the site. */
    @Column(length = 100)
    var name: String? = null,

    /** The picked region, e.g. `Marrakech,Marrakech-Safi,Morocco`. */
    @Column(length = 255)
    var region: String? = null,

    /** Free-text street or locality, e.g. `Targa`. Shown before [region]. */
    @Column(name = "address_detail", length = 255)
    var addressDetail: String? = null,

    /**
     * The map pin, independent of the text above.
     *
     * A typed address has no coordinates and a dropped pin may never be reverse
     * geocoded, so neither implies the other and both are nullable on their own.
     */
    var latitude: Double? = null,

    var longitude: Double? = null,

    /** Object-store URL. The instance's disk does not survive a deploy. */
    @Column(name = "image_url", length = 512)
    var imageUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
