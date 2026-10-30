package ma.electricwave.repository

import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): Optional<User>

    fun existsByEmail(email: String): Boolean

    fun existsByRole(role: Role): Boolean

    fun countByRole(role: Role): Long

    fun findAllByRoleOrderById(role: Role): List<User>
}
