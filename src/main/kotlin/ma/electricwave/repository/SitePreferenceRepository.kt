package ma.electricwave.repository

import ma.electricwave.domain.SitePreference
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SitePreferenceRepository : JpaRepository<SitePreference, Long> {

    /** The one row a user may have for a site, enforced unique by the schema. */
    fun findByUserIdAndAccessKeyId(userId: Long, accessKeyId: Long): Optional<SitePreference>

    /**
     * Every preference one user holds.
     *
     * Read on each sync and joined in memory against the user's keys, rather than one
     * lookup per key: a client with a dozen sites would otherwise issue a dozen queries
     * to render a list it already has in hand.
     */
    fun findAllByUserId(userId: Long): List<SitePreference>
}
