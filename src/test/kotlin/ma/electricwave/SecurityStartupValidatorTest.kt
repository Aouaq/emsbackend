package ma.electricwave

import ma.electricwave.config.DevelopmentDefaults
import ma.electricwave.config.SecurityStartupValidator
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `spring.profiles.default=dev` means a deployment that forgets `SPRING_PROFILES_ACTIVE`
 * activates the development profile and its published secrets. These cover the guard that
 * refuses to start in that situation.
 */
class SecurityStartupValidatorTest {

    @Test
    fun `rejects the published dev jwt secret outside the dev profile`() {
        val error = assertFailsWith<IllegalStateException> {
            validator(profile = "prod", jwtSecret = DevelopmentDefaults.JWT_SECRET)
                .afterPropertiesSet()
        }
        assertTrue(error.message!!.contains("app.jwt.secret"), error.message!!)
    }

    @Test
    fun `rejects the published dev crypto master key outside the dev profile`() {
        val error = assertFailsWith<IllegalStateException> {
            validator(profile = "prod", cryptoMasterKey = DevelopmentDefaults.CRYPTO_MASTER_KEY)
                .afterPropertiesSet()
        }
        assertTrue(error.message!!.contains("app.crypto.master-key"), error.message!!)
    }

    @Test
    fun `rejects the published dev admin password outside the dev profile`() {
        val error = assertFailsWith<IllegalStateException> {
            validator(profile = "prod", adminPassword = DevelopmentDefaults.ADMIN_PASSWORD)
                .afterPropertiesSet()
        }
        assertTrue(error.message!!.contains("app.admin.password"), error.message!!)
    }

    @Test
    fun `rejects dev secrets when no profile is set at all`() {
        // The case this guard exists for: an image started with no profile, which falls
        // back to dev. Spring resolves that default before beans initialise, but a bare
        // environment must not be treated as permission to use the published secrets.
        assertFailsWith<IllegalStateException> {
            validator(profile = null, jwtSecret = DevelopmentDefaults.JWT_SECRET)
                .afterPropertiesSet()
        }
    }

    @Test
    fun `allows the dev secrets inside the dev profile`() {
        validator(
            profile = "dev",
            jwtSecret = DevelopmentDefaults.JWT_SECRET,
            cryptoMasterKey = DevelopmentDefaults.CRYPTO_MASTER_KEY,
            adminPassword = DevelopmentDefaults.ADMIN_PASSWORD
        ).afterPropertiesSet()
    }

    @Test
    fun `allows real secrets in the prod profile`() {
        validator(
            profile = "prod",
            jwtSecret = "a-real-production-secret-of-at-least-32-bytes",
            cryptoMasterKey = "SGVsbG8sIHRoaXMgaXMgbm90IHRoZSBkZXYga2V5IQ==",
            adminPassword = "a-real-admin-password",
            trustedProxies = "10.0.0.1"
        ).afterPropertiesSet()
    }

    @Test
    fun `rejects exposed verification codes in the prod profile`() {
        val error = assertFailsWith<IllegalStateException> {
            validator(profile = "prod", exposeVerificationCode = true).afterPropertiesSet()
        }
        assertTrue(error.message!!.contains("expose-verification-code"), error.message!!)
    }

    @Test
    fun `a blank trusted-proxies list is a warning, not a failure`() {
        // A directly exposed instance is legitimate, so this must not block startup even
        // though it silently collapses the per-IP rate limits behind a proxy.
        validator(profile = "prod", trustedProxies = "").afterPropertiesSet()
    }

    private fun validator(
        profile: String?,
        exposeVerificationCode: Boolean = false,
        jwtSecret: String = "some-other-secret-that-is-long-enough-32",
        cryptoMasterKey: String = "c29tZS1vdGhlci0zMi1ieXRlLW1hc3Rlci1rZXkh",
        adminPassword: String = "some-other-admin-password",
        trustedProxies: String = ""
    ): SecurityStartupValidator {
        val environment = MockEnvironment()
        if (profile != null) {
            environment.setActiveProfiles(profile)
        }
        return SecurityStartupValidator(
            environment = environment,
            exposeVerificationCode = exposeVerificationCode,
            jwtSecret = jwtSecret,
            cryptoMasterKey = cryptoMasterKey,
            adminPassword = adminPassword,
            trustedProxies = trustedProxies
        )
    }
}
