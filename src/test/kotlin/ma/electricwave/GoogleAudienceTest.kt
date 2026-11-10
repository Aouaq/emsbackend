package ma.electricwave

import ma.electricwave.exception.ApiException
import ma.electricwave.security.NimbusGoogleTokenVerifier
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The accepted-audience list, tested without a Spring context or network.
 *
 * The decoder is built lazily and only reaches Google's JWKS endpoint when a token is
 * actually verified, so constructing the component here costs nothing and asserts on the
 * one piece of configuration that silently locks a whole platform out when it is wrong.
 */
class GoogleAudienceTest {

    private val web = "111-web.apps.googleusercontent.com"
    private val desktop = "111-desktop.apps.googleusercontent.com"

    @Test
    fun `accepts every client in a comma-separated list`() {
        val verifier = NimbusGoogleTokenVerifier("$web,$desktop")

        assertEquals(setOf(web, desktop), verifier.acceptedAudiences)
    }

    @Test
    fun `tolerates spacing and stray separators`() {
        // A list edited by hand in YAML picks these up; none should silently become a
        // client ID of "" that matches nothing or, worse, an unintended entry.
        val verifier = NimbusGoogleTokenVerifier("  $web , , $desktop,  ")

        assertEquals(setOf(web, desktop), verifier.acceptedAudiences)
    }

    @Test
    fun `a single client id still works`() {
        // The property held exactly one value before it became a list, and existing
        // deployments pass one through APP_OAUTH2_GOOGLE_CLIENT_ID.
        val verifier = NimbusGoogleTokenVerifier(web)

        assertEquals(setOf(web), verifier.acceptedAudiences)
    }

    @Test
    fun `unconfigured sign-in reports NOT_CONFIGURED rather than rejecting the token`() {
        val verifier = NimbusGoogleTokenVerifier("")

        assertTrue(verifier.acceptedAudiences.isEmpty())
        val ex = assertFailsWith<ApiException> { verifier.verify("any.token.value") }

        // 503/NOT_CONFIGURED tells the client the server is missing setup; a 401 would
        // send them off debugging a token that was never the problem.
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status)
        assertEquals("NOT_CONFIGURED", ex.code)
    }

    @Test
    fun `whitespace-only configuration counts as unconfigured`() {
        val verifier = NimbusGoogleTokenVerifier("   ,  , ")

        assertTrue(verifier.acceptedAudiences.isEmpty())
    }

    // --- expiry detection -------------------------------------------------------------
    //
    // Expiry is reported back as its own code so a client can drop a cached token and
    // fetch a fresh one. The only signal Spring offers is the error description text, so
    // these pin the matching: the real wording must be recognised, and nothing else may
    // be mistaken for it.

    private fun validationFailure(description: String) = JwtValidationException(
        "decode failed",
        listOf(OAuth2Error("invalid_token", description, null))
    )

    @Test
    fun `recognises the decoder's expiry wording`() {
        val verifier = NimbusGoogleTokenVerifier(web)

        assertTrue(
            verifier.isExpiry(validationFailure("Jwt expired at 2026-11-10T12:17:47Z"))
        )
    }

    @Test
    fun `does not mistake a wrong audience for expiry`() {
        val verifier = NimbusGoogleTokenVerifier(web)

        assertFalse(
            verifier.isExpiry(
                validationFailure("Google ID token was issued for another client")
            )
        )
    }

    @Test
    fun `falls back to the message for a non-validation failure`() {
        val verifier = NimbusGoogleTokenVerifier(web)

        assertTrue(verifier.isExpiry(JwtException("Jwt expired at some point")))
        assertFalse(verifier.isExpiry(JwtException("Signed JWT rejected: invalid signature")))
    }
}
