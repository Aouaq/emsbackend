package ma.electricwave.security

import ma.electricwave.exception.ApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
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
    @Value("\${app.oauth2.google.client-id:}") rawClientIds: String
) : GoogleTokenVerifier {

    /**
     * Every Google OAuth client whose ID tokens this backend accepts, parsed from a
     * comma-separated list.
     *
     * More than one is the normal case rather than an edge case: each platform signs in
     * with its own OAuth client, and the ID token carries that client as its `aud`. An
     * Android build authenticating against the Web client, a macOS build against a macOS
     * client and a desktop build against an installed-app client present three different
     * audiences for the same person. Accepting only one means the other platforms cannot
     * sign in at all, failing as "issued for another client".
     *
     * This is an allow-list, so every entry must be a client of this Google project. The
     * audience is what proves the token was minted for us; widening it to a client
     * someone else controls would let them mint tokens this backend trusts.
     */
    internal val acceptedAudiences: Set<String> = rawClientIds.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val decoder: JwtDecoder? by lazy {
        if (acceptedAudiences.isEmpty()) null else buildDecoder()
    }

    override fun verify(idToken: String): GoogleUser {
        val jwtDecoder = decoder
            ?: throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured")

        val jwt = try {
            jwtDecoder.decode(idToken)
        } catch (ex: JwtException) {
            // The decoder says precisely which check failed - audience, issuer, expiry,
            // signature - and that distinction is the entire diagnosis when one platform
            // cannot sign in while another can. Collapsing every cause into a single
            // message left a log of identical lines and no way to tell them apart.
            // The caller still gets the generic message: which check failed is not a
            // client's business, and saying so would help someone probe for a token
            // this server would accept.
            log.warn("Rejected Google ID token: {}", ex.message)
            throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token")
        } catch (ex: IllegalArgumentException) {
            log.warn("Malformed Google ID token: {}", ex.message)
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

        // Plain validators rather than JwtClaimValidator: that helper's type parameter is
        // bounded by Any, so a claim the token simply omits arrives as null against a
        // non-null Kotlin parameter and throws inside the decoder, surfacing as 500
        // instead of the 401 an unacceptable token deserves.
        val issuerValidator = claimValidator("Google ID token has an unexpected issuer") { token ->
            val issuer = token.issuer?.toString()
            issuer == "https://accounts.google.com" || issuer == "accounts.google.com"
        }
        val audienceValidator = claimValidator("Google ID token was issued for another client") { token ->
            token.audience?.any { it in acceptedAudiences } == true
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

    private fun claimValidator(
        description: String,
        accept: (Jwt) -> Boolean
    ): OAuth2TokenValidator<Jwt> = OAuth2TokenValidator { token ->
        if (accept(token)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NimbusGoogleTokenVerifier::class.java)
        private const val GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs"
    }
}
