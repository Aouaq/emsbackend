package ma.electricwave

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        "app.rate-limit.login-ip-per-minute=1000",
        "app.rate-limit.login-email-per-minute=1000",
        "app.rate-limit.register-ip-per-hour=1000",
        "app.rate-limit.verify-ip-per-10-minutes=1000",
        "app.rate-limit.resend-email-per-10-minutes=1000",
        "app.rate-limit.resend-ip-per-hour=1000",
        "app.rate-limit.refresh-ip-per-minute=1000",
        "app.rate-limit.google-ip-per-10-minutes=1000"
    ]
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class MobileApiIntegrationTest
