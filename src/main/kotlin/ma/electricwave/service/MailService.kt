package ma.electricwave.service

import ma.electricwave.service.mail.MailTransport
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Outcome of an attempted send, so callers can stop asserting delivery they cannot see. */
enum class MailDelivery {
    /** Accepted by the transport without error. */
    SENT,

    /** `app.mail.enabled=false`; nothing was attempted. Expected in development. */
    DISABLED,

    /** Sending was attempted and failed. The recipient has nothing. */
    FAILED
}

@Service
class MailService(
    private val transport: MailTransport,
    @Value("\${app.mail.enabled}") private val mailEnabled: Boolean,
    @Value("\${app.mail.from}") private val from: String
) {

    private val log = LoggerFactory.getLogger(MailService::class.java)

    val enabled: Boolean
        get() = mailEnabled

    fun sendVerificationCode(to: String, code: String): MailDelivery =
        send(
            to,
            "Your ElectricWave verification code",
            """
                Welcome to ElectricWave!

                Your verification code is: $code

                This code expires in 10 minutes. If you did not request it, you can ignore this email.
            """.trimIndent(),
            "verification"
        )

    fun sendPasswordResetCode(to: String, code: String): MailDelivery =
        send(
            to,
            "Your ElectricWave password reset code",
            """
                A password reset was requested for your ElectricWave account.

                Your reset code is: $code

                This code expires in 10 minutes. If you did not request it, you can ignore this email.
            """.trimIndent(),
            "password reset"
        )

    /**
     * What is sent and whether to send at all is decided here; how it travels is
     * [MailTransport]'s business. The two are separated because a deployed platform can
     * block SMTP outright - see
     * [ma.electricwave.service.mail.BrevoMailTransport] - and that is a question about
     * the network, not about verification emails.
     */
    private fun send(to: String, subject: String, text: String, kind: String): MailDelivery {
        if (!mailEnabled) {
            log.warn("Mail is disabled (app.mail.enabled=false). {} code for {} not sent by email.", kind, to)
            return MailDelivery.DISABLED
        }
        return try {
            transport.send(from, to, subject, text)
            MailDelivery.SENT
        } catch (ex: Exception) {
            log.error("Failed to send {} email to {}", kind, to, ex)
            MailDelivery.FAILED
        }
    }
}
