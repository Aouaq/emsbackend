package ma.electricwave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true, length = 255)
    var email: String = "",

    @Column(name = "password_hash")
    var password: String? = null,

    @Column(name = "full_name")
    var fullName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: Role = Role.CLIENT,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    var authProvider: AuthProvider = AuthProvider.LOCAL,

    @Column(name = "is_verified", nullable = false)
    var isVerified: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
) {

    @ManyToMany
    @JoinTable(
        name = "user_access_keys",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "access_key_id")]
    )
    var accessKeys: MutableSet<AccessKey> = mutableSetOf()

    fun isAdmin(): Boolean = role == Role.ADMIN
}
