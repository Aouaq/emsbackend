package ma.electricwave.security

import ma.electricwave.exception.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

data class GoogleUser(
    val email: String,
    val name: String?
)

interface GoogleTokenVerifier {
    fun verify(idToken: String): GoogleUser
}

@Component
class NimbusGoogleTokenVerifier(
    @Value("\${app.oauth2.google.client-id:}") private val clientId: String
) : GoogleTokenVerifier {

    private val decoder: JwtDecoder? by lazy {
        if (clientId.isBlank()) null else buildDecoder()
    }

    override fun verify(idToken: String): GoogleUser {
        val jwtDecoder = decoder
            ?: throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured")

        val jwt = try {
            jwtDecoder.decode(idToken)
        } catch (ex: JwtException) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token")
        } catch (ex: IllegalArgumentException) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token")
        }

        val email = jwt.getClaimAsString("email")
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Google account has no email")
        val verified = when (val value = jwt.claims["email_verified"]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
        if (!verified) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Google email is not verified")
        }

        return GoogleUser(email = email, name = jwt.getClaimAsString("name"))
    }

    private fun buildDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

        val issuerValidator = JwtClaimValidator<String>(JwtClaimNames.ISS) { issuer ->
            issuer == "https://accounts.google.com" || issuer == "accounts.google.com"
        }
        val audienceValidator = JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { audience ->
            audience.contains(clientId)
        }

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefault(),
                issuerValidator,
                audienceValidator
            )
        )
        return decoder
    }

    companion object {
        private const val GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs"
    }
}
