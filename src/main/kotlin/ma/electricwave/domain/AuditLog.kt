package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "audit_log")
class AuditLog(
    @Column(name = "event_type", nullable = false, length = 64)
    var eventType: String = "",

    @Column(name = "actor_user_id")
    var actorUserId: Long? = null,

    @Column(name = "actor_email")
    var actorEmail: String? = null,

    @Column(name = "target_user_id")
    var targetUserId: Long? = null,

    @Column(name = "target_email")
    var targetEmail: String? = null,

    @Column(length = 100)
    var resource: String? = null,

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var details: Map<String, Any?>? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
