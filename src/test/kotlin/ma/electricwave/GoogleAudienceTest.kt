package ma.electricwave

import ma.electricwave.exception.ApiException
import ma.electricwave.security.NimbusGoogleTokenVerifier
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
