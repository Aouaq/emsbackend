package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * The limits one site is judged against.
 *
 * Both are nullable and a null disables that check, which is not the same as zero: a
 * ceiling of zero watts is a limit every live site breaches permanently. A site with no
 * row at all uses the application defaults, so a newly registered installation is
 * watched from the moment it exists.
 */
@Entity
@Table(name = "alarm_thresholds")
class AlarmThreshold(
    @Id
    @Column(name = "access_key_id")
    var accessKeyId: Long = 0,

    /** Alarm when total power rises above this, in watts. */
    @Column(name = "max_power")
    var maxPower: Double? = null,

    /** Alarm when voltage falls below this, in volts. */
    @Column(name = "min_voltage")
    var minVoltage: Double? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
