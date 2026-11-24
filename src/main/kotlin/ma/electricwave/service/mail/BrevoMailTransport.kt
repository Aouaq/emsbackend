package ma.electricwave.service.mail

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

/**
 * Sends through Brevo's HTTP API, for deployments where outbound SMTP is blocked.
 *
 * Render drops outbound traffic to ports 25, 465 and 587, and a dropped packet is worse
 * than a closed port: the connect does not fail, it hangs for the full connect timeout
 * and then reports `SocketTimeoutException` - once per send, and once more per health
 * check. No `spring.mail` value changes that. This transport carries the same message
 * over 443, which no platform blocks because doing so would break everything else.
 *
 * Only the wire differs. The message bodies, the decision to send, and the
 * [ma.electricwave.service.MailDelivery] a caller sees are all unchanged.
 */
@Component
@ConditionalOnProperty(name = ["app.mail.transport"], havingValue = "brevo")
class BrevoMailTransport(
    @Value("\${app.mail.api-key:}") apiKey: String,
    @Value("\${app.mail.api-base-url:https://api.brevo.com/v3}") baseUrl: String
) : MailTransport {

    init {
        // Fails the context rather than each send. Without a key every registration would
        // answer "the verification email could not be sent" and the reason would live
        // only in the logs, which is a bad way to learn that a deploy is missing a
        // variable. Note this is the API key, not the separate SMTP key Brevo issues for
        // its relay; the two are not interchangeable.
        require(apiKey.isNotBlank()) {
            "app.mail.api-key (APP_MAIL_API_KEY) must be set when app.mail.transport=brevo"
        }
    }

    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        // Brevo authenticates with a bare key in its own header, not a Bearer token.
        .defaultHeader(API_KEY_HEADER, apiKey)
        // Explicitly the JDK client, not the SimpleClientHttpRequestFactory default.
        // That one wraps HttpURLConnection, which on a 401 goes looking for an
        // Authenticator and hands back an empty error stream - so the body is lost on
        // exactly the failure most likely to happen, a wrong API key, leaving
        // "HTTP 401" with nothing after it. See BrevoMailTransportTest.
        .requestFactory(
            JdkClientHttpRequestFactory(
                // A transactional send runs on a request thread, so an unresponsive
                // provider must not be able to hold one open indefinitely. Well under
                // the 20s SMTP allowance: an HTTP API that has not answered in ten
                // seconds is not about to.
                HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    // HTTP/1.1 rather than the JDK client's HTTP/2 default. This is one
                    // small POST per email, so multiplexing and header compression buy
                    // nothing measurable, and pinning the version takes protocol
                    // negotiation out of the failure modes entirely.
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
            ).apply { setReadTimeout(READ_TIMEOUT) }
        )
        .build()

    override fun send(from: String, to: String, subject: String, text: String) {
        // `textContent`, `htmlContent` and `templateId` are mutually exclusive in this
        // API and exactly one must be present. These messages are plain text.
        val payload = mapOf(
            "sender" to mapOf("email" to from),
            "to" to listOf(mapOf("email" to to)),
            "subject" to subject,
            "textContent" to text
        )

        try {
            client.post()
                .uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (ex: RestClientResponseException) {
            // Brevo answers a rejection with {"code":..,"message":..}, and an unverified
            // sender, an exhausted quota and a bad key each say so by name. The body goes
            // through verbatim rather than parsed: there is nothing to add to it, and a
            // proxy's HTML error page survives the same path instead of failing to parse.
            throw MailTransportException(
                "Brevo rejected the send with HTTP ${ex.statusCode.value()}: " +
                    ex.responseBodyAsString.take(MAX_REPORTED_BODY).ifBlank { "(empty body)" }
            )
        }
    }

    private companion object {
        const val API_KEY_HEADER = "api-key"

        /** Enough for Brevo's own JSON; a cap only in case something else answers. */
        const val MAX_REPORTED_BODY = 500

        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
