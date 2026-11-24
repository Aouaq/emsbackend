package ma.electricwave

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import ma.electricwave.service.mail.BrevoMailTransport
import ma.electricwave.service.mail.MailTransportException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The HTTP transport, against a stub of Brevo rather than Brevo.
 *
 * This is the one piece of the mail path that cannot be checked by reading it: the
 * provider's contract is external, and getting a field name or the authentication header
 * wrong fails only at runtime, in a deployed environment, as a verification email that
 * never arrives. So the request itself is the assertion.
 *
 * No Spring context: the component takes two strings, which is the whole point of
 * keeping the transport separate from what it carries.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrevoMailTransportTest {

    private val server = WireMockServer(options().dynamicPort())

    private val apiKey = "xkeysib-test-key"
    private val from = "no-reply@electricwave.ma"
    private val to = "client@example.com"

    private fun transport() = BrevoMailTransport(apiKey, "http://localhost:${server.port()}/v3")

    @BeforeAll
    fun start() = server.start()

    @AfterAll
    fun stop() = server.stop()

    @BeforeEach
    fun reset() = server.resetAll()

    @Test
    fun `a send posts the payload Brevo documents`() {
        server.stubFor(
            post(urlEqualTo(PATH)).willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"messageId":"<202611101607.1@relay.brevo.com>"}""")
            )
        )

        transport().send(from, to, "Your ElectricWave verification code", "Your code is 123456")

        // equalToJson is strict about extra fields, so this also pins that htmlContent
        // and templateId are absent: the API accepts exactly one of the three body
        // types, and sending a second is a 400.
        server.verify(
            postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                          "sender": { "email": "$from" },
                          "to": [ { "email": "$to" } ],
                          "subject": "Your ElectricWave verification code",
                          "textContent": "Your code is 123456"
                        }
                        """.trimIndent()
                    )
                )
        )
    }

    @Test
    fun `the key travels in Brevo's own header, not as a bearer token`() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(201)))

        transport().send(from, to, "subject", "body")

        // Brevo takes a bare key in `api-key`. Sending it as `Authorization: Bearer ...`
        // instead is a 401 that reads like an invalid key rather than a misplaced one.
        server.verify(
            postRequestedFor(urlEqualTo(PATH))
                .withHeader("api-key", equalTo(apiKey))
                .withHeader("Authorization", absent())
        )
    }

    @Test
    fun `a rejection reports Brevo's own reason, without a stack trace`() {
        server.stubFor(
            post(urlEqualTo(PATH)).willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"code":"unauthorized","message":"Key not found"}""")
            )
        )

        val ex = assertFailsWith<MailTransportException> {
            transport().send(from, to, "subject", "body")
        }

        // The status alone does not distinguish a bad key from an unverified sender or
        // an exhausted quota; Brevo's own code and message do.
        assertTrue(ex.message!!.contains("401"), "expected the status, got: ${ex.message}")
        assertTrue(ex.message!!.contains("unauthorized"), "expected Brevo's code, got: ${ex.message}")
        assertTrue(ex.message!!.contains("Key not found"), "expected Brevo's message, got: ${ex.message}")

        // A rejected send is an operational event, not a defect. The frames said nothing
        // and buried the line that did; see MailTransportException.
        assertEquals(0, ex.stackTrace.size, "MailTransportException should carry no stack trace")
    }

    @Test
    fun `a provider fault is still reported rather than silently dropped`() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503).withBody("upstream down")))

        val ex = assertFailsWith<MailTransportException> {
            transport().send(from, to, "subject", "body")
        }

        assertTrue(ex.message!!.contains("503"), "expected the status, got: ${ex.message}")
    }

    @Test
    fun `an empty error body does not produce a message that says nothing`() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)))

        val ex = assertFailsWith<MailTransportException> {
            transport().send(from, to, "subject", "body")
        }

        assertTrue(ex.message!!.contains("(empty body)"), "expected a placeholder, got: ${ex.message}")
    }

    @Test
    fun `selecting this transport without a key fails at construction`() {
        // Not per send: a deployment missing APP_MAIL_API_KEY should refuse to start,
        // rather than accept every registration and quietly deliver none of them.
        val ex = assertFailsWith<IllegalArgumentException> {
            BrevoMailTransport("   ", "http://localhost:${server.port()}/v3")
        }

        assertTrue(
            ex.message!!.contains("APP_MAIL_API_KEY"),
            "the failure should name the variable to set, got: ${ex.message}"
        )
    }

    private companion object {
        const val PATH = "/v3/smtp/email"
    }
}
