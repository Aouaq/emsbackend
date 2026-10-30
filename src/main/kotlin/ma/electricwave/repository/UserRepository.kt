package ma.electricwave.repository

import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): Optional<User>

    fun existsByEmail(email: String): Boolean

    fun existsByRole(role: Role): Boolean

    fun countByRole(role: Role): Long

    fun countByIsVerifiedTrue(): Long

    fun countByDisabledAtIsNotNull(): Long

    fun findAllByRoleOrderById(role: Role): List<User>

    @Query(
        """
        select u from User u
        where lower(u.email) like lower(concat('%', :query, '%'))
           or lower(coalesce(u.fullName, '')) like lower(concat('%', :query, '%'))
        """
    )
    fun searchByQuery(@Param("query") query: String, pageable: Pageable): Page<User>
}
