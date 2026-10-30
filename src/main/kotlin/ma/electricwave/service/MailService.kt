package ma.electricwave.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.enabled}") private val mailEnabled: Boolean,
    @Value("\${app.mail.from}") private val from: String
) {

    private val log = LoggerFactory.getLogger(MailService::class.java)

    val enabled: Boolean
        get() = mailEnabled

    fun sendVerificationCode(to: String, code: String) {
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
    }

    fun sendPasswordResetCode(to: String, code: String) {
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
    }

    private fun send(to: String, subject: String, text: String, kind: String) {
        if (!mailEnabled) {
            log.warn("Mail is disabled (app.mail.enabled=false). {} code for {} not sent by email.", kind, to)
            return
        }
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            this.subject = subject
            this.text = text
        }
        try {
            mailSender.send(message)
        } catch (ex: Exception) {
            log.error("Failed to send {} email to {}", kind, to, ex)
        }
    }
}
