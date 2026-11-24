package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A note that the seeder has already dealt with this site.
 *
 * Its presence - **not** the presence of an [AlarmThreshold] - is what stops the seeder
 * acting. The two are deliberately separate facts: a threshold row says what the limits
 * are right now, and this says the configuration file has already been applied once.
 * Only this one survives a reset, which is what lets a reset be permanent.
 */
@Entity
@Table(name = "alarm_threshold_seeds")
class AlarmThresholdSeed(
    @Id
    @Column(name = "access_key_id")
    var accessKeyId: Long = 0,

    @Column(name = "seeded_at", nullable = false)
    var seededAt: Instant = Instant.now()
)
