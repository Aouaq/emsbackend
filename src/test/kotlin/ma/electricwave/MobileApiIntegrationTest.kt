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

        // No site monitoring during tests. Left on, the scheduled sweep would reach a
        // real Emoncms over the network from every test class that boots a context,
        // making the suite depend on an external host being up and on the seeded keys
        // still decrypting - neither of which is what any of these tests is about.
        "app.monitor.enabled=false",

        // The fleet defaults are operational configuration, and they get tuned against
        // real readings: the voltage floor moved from 230 V to 210 V the first time the
        // monitor ran against the live fleet, because 230 V sat inside the normal
        // operating range of several sites. A test asserting "a site with no row of its
        // own reports the defaults" is about that mechanism, not about either number, so
        // the numbers are pinned here rather than tracking application.yml - otherwise
        // every future tuning turns the suite red for no defect.
        "app.monitor.default-max-power=142000",
        "app.monitor.default-min-voltage=230",

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
