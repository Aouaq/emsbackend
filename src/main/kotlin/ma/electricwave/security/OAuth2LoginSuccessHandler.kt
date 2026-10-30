package ma.electricwave.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@Component
class OAuth2LoginSuccessHandler(
    private val jwtService: JwtService,
    private val userRepository: ma.electricwave.repository.UserRepository,
    @Value("\${app.oauth2.success-url}") private val successUrl: String
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val email = (authentication.principal as? OidcUser)?.email
        val user = email?.lowercase()?.let { userRepository.findByEmail(it).orElse(null) }

        if (user == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User account could not be resolved")
            return
        }

        val token = jwtService.issue(user)
        val separator = if (successUrl.contains('?')) "&" else "?"
        response.sendRedirect("$successUrl${separator}token=${UriUtils.encode(token, StandardCharsets.UTF_8)}")
    }
}
