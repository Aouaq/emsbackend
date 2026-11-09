package ma.electricwave.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.stereotype.Service

/** Outcome of an attempted send, so callers can stop asserting delivery they cannot see. */
enum class MailDelivery {
    /** Handed to the SMTP server without error. */
    SENT,

    /** `app.mail.enabled=false`; nothing was attempted. Expected in development. */
    DISABLED,

    /** Sending was attempted and failed. The recipient has nothing. */
    FAILED
}

@Service
class MailService(
    private val mailSender: JavaMailSender,
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

    private fun send(to: String, subject: String, text: String, kind: String): MailDelivery {
        if (!mailEnabled) {
            log.warn("Mail is disabled (app.mail.enabled=false). {} code for {} not sent by email.", kind, to)
            return MailDelivery.DISABLED
        }
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            this.subject = subject
            this.text = text
        }
        return try {
            mailSender.send(message)
            MailDelivery.SENT
        } catch (ex: Exception) {
            log.error("Failed to send {} email to {}", kind, to, ex)
            MailDelivery.FAILED
        }
    }
}
