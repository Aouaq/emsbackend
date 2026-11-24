package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** What tripped. */
enum class AlarmKind {
    /** Total power above the site's ceiling. */
    OVER_POWER,

    /** Voltage below the site's floor. */
    UNDER_VOLTAGE
}

/**
 * One crossing: raised when a reading passed a limit, cleared when it came back.
 *
 * `clearedAt == null` means the alarm is open, and a partial unique index on
 * `(access_key_id, kind) where cleared_at is null` makes "at most one open alarm per
 * site and kind" a database rule rather than a convention the poller has to maintain.
 */
@Entity
@Table(name = "alarms")
class Alarm(
    @Column(name = "access_key_id", nullable = false)
    var accessKeyId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var kind: AlarmKind = AlarmKind.OVER_POWER,

    @Column(name = "raised_at", nullable = false)
    var raisedAt: Instant = Instant.now(),

    @Column(name = "cleared_at")
    var clearedAt: Instant? = null,

    /**
     * The reading that tripped it and the limit it crossed, both stored.
     *
     * The threshold is copied rather than referenced because limits get edited: an entry
     * holding only the reading stops explaining itself the moment someone retunes the
     * site, and the history is most useful precisely when you are deciding whether the
     * old limit was right.
     */
    @Column(nullable = false)
    var value: Double = 0.0,

    @Column(nullable = false)
    var threshold: Double = 0.0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) {
    val isActive: Boolean get() = clearedAt == null
}
