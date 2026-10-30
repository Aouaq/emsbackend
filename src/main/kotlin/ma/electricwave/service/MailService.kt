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
        if (!mailEnabled) {
            log.warn("Mail is disabled (app.mail.enabled=false). Verification code for {} not sent by email.", to)
            return
        }
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            subject = "Your ElectricWave verification code"
            text = """
                Welcome to ElectricWave!

                Your verification code is: $code

                This code expires in 10 minutes. If you did not request it, you can ignore this email.
            """.trimIndent()
        }
        try {
            mailSender.send(message)
        } catch (ex: Exception) {
            log.error("Failed to send verification email to {}", to, ex)
        }
    }
}
