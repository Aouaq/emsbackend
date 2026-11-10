package ma.electricwave

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        // ApiTestSupport.register() reads `devVerificationCode` from the response, which
        // AuthService only returns when the code is exposed AND mail is off. Both are
        // stated here rather than inherited, so ambient configuration cannot silently
        // turn the whole suite red.
        "app.mail.enabled=false",
        "app.auth.expose-verification-code=true",

        "app.rate-limit.login-ip-per-minute=1000",
        "app.rate-limit.login-email-per-minute=1000",
        "app.rate-limit.register-ip-per-hour=1000",
        "app.rate-limit.verify-ip-per-10-minutes=1000",
        "app.rate-limit.resend-email-per-10-minutes=1000",
        "app.rate-limit.resend-ip-per-hour=1000",
        "app.rate-limit.refresh-ip-per-minute=1000",
        "app.rate-limit.google-ip-per-10-minutes=1000",
        "app.rate-limit.claim-per-user-per-minute=1000",
        "app.rate-limit.claim-per-ip-per-minute=1000",
        "app.rate-limit.forgot-per-email-per-hour=1000",
        "app.rate-limit.forgot-per-ip-per-hour=1000",
        "app.rate-limit.reset-per-ip-per-10-minutes=1000",
        "app.rate-limit.change-password-per-user-per-10-minutes=1000"
    ]
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class MobileApiIntegrationTest
