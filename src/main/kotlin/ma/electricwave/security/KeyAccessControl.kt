package ma.electricwave.security

import ma.electricwave.repository.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component("keyAccess")
class KeyAccessControl(private val userRepository: UserRepository) {

    @Transactional(readOnly = true)
    fun hasAccess(authentication: Authentication?, resource: String): Boolean {
        if (authentication == null || !authentication.isAuthenticated) return false
        val email = authentication.name?.lowercase() ?: return false
        val user = userRepository.findByEmail(email).orElse(null) ?: return false
        if (user.isAdmin()) return true
        return user.accessKeys.any { it.resource == resource && it.isActive }
    }
}
