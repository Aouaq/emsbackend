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

    /**
     * `query` must have its LIKE metacharacters escaped by the caller with `!`; see
     * [ma.electricwave.service.AdminUserService.escapeLikePattern]. Unescaped, a search
     * for `%` matches every row and `_` matches any single character.
     */
    @Query(
        """
        select u from User u
        where lower(u.email) like lower(concat('%', :query, '%')) escape '!'
           or lower(coalesce(u.fullName, '')) like lower(concat('%', :query, '%')) escape '!'
        """
    )
    fun searchByQuery(@Param("query") query: String, pageable: Pageable): Page<User>
}
