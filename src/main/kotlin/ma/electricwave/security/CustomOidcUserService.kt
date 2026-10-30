package ma.electricwave.security

import ma.electricwave.domain.AuthProvider
import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.repository.UserRepository
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class CustomOidcUserService(private val userRepository: UserRepository) : OidcUserService() {

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = super.loadUser(userRequest)
        val email = oidcUser.email
            ?: throw OAuth2AuthenticationException(OAuth2Error("invalid_registration"), "Google account has no email claim")

        val normalised = email.lowercase()
        val existing = userRepository.findByEmail(normalised).orElse(null)
        if (existing == null) {
            userRepository.save(
                User(
                    email = normalised,
                    fullName = oidcUser.fullName,
                    role = Role.CLIENT,
                    authProvider = AuthProvider.GOOGLE,
                    isVerified = true
                )
            )
        } else if (!existing.isVerified) {
            existing.isVerified = true
            userRepository.save(existing)
        }
        return oidcUser
    }
}
