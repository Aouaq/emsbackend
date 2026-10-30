package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "access_keys")
class AccessKey(
    @Column(name = "key_value", nullable = false, unique = true, length = 255)
    var keyValue: String = "",

    @Column(nullable = false, length = 128)
    var resource: String = "",

    @Column(length = 512)
    var description: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) {

    @ManyToMany(mappedBy = "accessKeys")
    var linkedUsers: MutableSet<User> = mutableSetOf()
}
