package ma.electricwave

import io.mockk.mockk
import ma.electricwave.service.mail.BrevoMailTransport
import ma.electricwave.service.mail.MailTransport
import ma.electricwave.service.mail.SmtpMailTransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import java.util.function.Supplier

/** Scans only the transport package: `MailService` itself is not under test here. */
@Configuration
@ComponentScan("ma.electricwave.service.mail")
internal class MailTransportScan

/**
 * Which transport bean `app.mail.transport` actually selects.
 *
 * Worth its own test because the failure mode is a context that will not start, and the
 * only place that shows up otherwise is a deployment. Two beans matching, or none,
 * breaks `MailService`'s constructor; a conditional that never matches `brevo` leaves a
 * deploy silently on the SMTP path it was moved off.
 *
 * Runs without a database or a network - the conditionals are configuration, not
 * behaviour, so the context runner is enough.
 */
class MailTransportSelectionTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(MailTransportScan::class.java)
        // SmtpMailTransport needs one; it is never called, only injected.
        .withBean(JavaMailSender::class.java, Supplier { mockk<JavaMailSender>() })

    @Test
    fun `no property configured keeps the SMTP transport`() {
        // matchIfMissing: a configuration predating app.mail.transport must behave as it
        // did before the property existed.
        runner.run { context ->
            assertThat(context).hasSingleBean(MailTransport::class.java)
            assertThat(context).hasSingleBean(SmtpMailTransport::class.java)
            assertThat(context).doesNotHaveBean(BrevoMailTransport::class.java)
        }
    }

    @Test
    fun `smtp selects exactly one transport`() {
        runner.withPropertyValues("app.mail.transport=smtp").run { context ->
            assertThat(context).hasSingleBean(MailTransport::class.java)
            assertThat(context).hasSingleBean(SmtpMailTransport::class.java)
        }
    }

    @Test
    fun `brevo swaps the transport rather than adding one`() {
        runner
            .withPropertyValues(
                "app.mail.transport=brevo",
                "app.mail.api-key=xkeysib-test-key"
            )
            .run { context ->
                // Exactly one: a second bean would fail MailService's constructor, and
                // the SMTP one still has a usable JavaMailSender here, so nothing but
                // the conditional stops it being created.
                assertThat(context).hasSingleBean(MailTransport::class.java)
                assertThat(context).hasSingleBean(BrevoMailTransport::class.java)
                assertThat(context).doesNotHaveBean(SmtpMailTransport::class.java)
            }
    }

    @Test
    fun `brevo without a key refuses to start, naming the variable`() {
        // The point of failing here rather than per send: a deploy missing
        // APP_MAIL_API_KEY should not come up and accept registrations it cannot deliver.
        runner.withPropertyValues("app.mail.transport=brevo").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .hasRootCauseMessage(
                    "app.mail.api-key (APP_MAIL_API_KEY) must be set when app.mail.transport=brevo"
                )
        }
    }

    @Test
    fun `an unrecognised transport leaves no transport at all`() {
        // Neither conditional matches, so nothing is registered - and because
        // MailService requires a MailTransport, the real context cannot start at all.
        // That is the wanted outcome: a typo in APP_MAIL_TRANSPORT should stop a deploy
        // rather than quietly fall back to a default and deliver nothing.
        runner.withPropertyValues("app.mail.transport=sendgrid").run { context ->
            assertThat(context).doesNotHaveBean(MailTransport::class.java)
        }
    }
}
