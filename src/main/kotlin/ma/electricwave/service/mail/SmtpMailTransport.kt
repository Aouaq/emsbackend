package ma.electricwave.service.mail

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Sends over SMTP with Spring's [JavaMailSender], configured by `spring.mail.*`.
 *
 * The default, and the development path: the local stack talks to a relay directly and
 * `app.mail.enabled=false` means it usually sends nothing at all.
 *
 * `matchIfMissing = true` is what keeps a configuration that has never heard of
 * `app.mail.transport` behaving exactly as it did before the property existed.
 */
@Component
@ConditionalOnProperty(name = ["app.mail.transport"], havingValue = "smtp", matchIfMissing = true)
class SmtpMailTransport(private val mailSender: JavaMailSender) : MailTransport {

    override fun send(from: String, to: String, subject: String, text: String) {
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            this.subject = subject
            this.text = text
        }
        mailSender.send(message)
    }
}
