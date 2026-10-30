package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "access_keys")
class AccessKey(
    @Column(nullable = false, unique = true, length = 100)
    var resource: String = "",

    @Column(length = 255)
    var description: String? = null,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    var keyHash: String = "",

    @Column(name = "key_hint", nullable = false, length = 16)
    var keyHint: String = "",

    @Column(name = "emoncms_api_key_enc", nullable = false)
    var emoncmsApiKeyEnc: ByteArray = ByteArray(0),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) {

    @ManyToMany(mappedBy = "accessKeys")
    var linkedUsers: MutableSet<User> = mutableSetOf()
}
