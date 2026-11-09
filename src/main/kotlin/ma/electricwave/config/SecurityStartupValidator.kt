package ma.electricwave.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * Fails fast on configuration that is safe in development but dangerous once deployed.
 *
 * `application.yml` sets `spring.profiles.default=dev`, so a deployment that forgets
 * `SPRING_PROFILES_ACTIVE` activates the development profile and its published secrets.
 * The Dockerfile pins the profile; these checks are the second line of defence for
 * deployments that do not go through that image, and for environments that set some but
 * not all of the required secrets.
 */
@Component
class SecurityStartupValidator(
    private val environment: Environment,
    @Value("\${app.auth.expose-verification-code:false}") private val exposeVerificationCode: Boolean,
    @Value("\${app.jwt.secret:}") private val jwtSecret: String,
    @Value("\${app.crypto.master-key:}") private val cryptoMasterKey: String,
    @Value("\${app.admin.password:}") private val adminPassword: String,
    @Value("\${app.trusted-proxies:}") private val trustedProxies: String
) : InitializingBean {

    private val log = LoggerFactory.getLogger(SecurityStartupValidator::class.java)

    override fun afterPropertiesSet() {
        val isDev = environment.acceptsProfiles(Profiles.of("dev"))
        val isProd = environment.acceptsProfiles(Profiles.of("prod"))

        if (exposeVerificationCode && isProd) {
            throw IllegalStateException(
                "app.auth.expose-verification-code must be false in the prod profile"
            )
        }

        if (isDev) {
            // Not an error, but the one line an operator needs to see if they believed
            // they were starting a deployed instance.
            log.warn(
                "Starting with the 'dev' profile: published development secrets are in use " +
                    "(JWT signing key, crypto master key, bootstrap admin password). " +
                    "Set SPRING_PROFILES_ACTIVE=prod for any deployed environment."
            )
            return
        }

        val offenders = buildList {
            if (jwtSecret == DevelopmentDefaults.JWT_SECRET) add("app.jwt.secret (APP_JWT_SECRET)")
            if (cryptoMasterKey == DevelopmentDefaults.CRYPTO_MASTER_KEY) {
                add("app.crypto.master-key (APP_CRYPTO_MASTER_KEY)")
            }
            if (adminPassword == DevelopmentDefaults.ADMIN_PASSWORD) {
                add("app.admin.password (APP_ADMIN_PASSWORD)")
            }
        }
        if (offenders.isNotEmpty()) {
            throw IllegalStateException(
                "Refusing to start outside the dev profile with published development " +
                    "secrets still configured: ${offenders.joinToString(", ")}. " +
                    "These values are committed to the repository and must be replaced."
            )
        }

        if (isProd && trustedProxies.isBlank()) {
            // Not fatal: a directly exposed instance is a legitimate deployment. But when
            // the app is behind a proxy, every request carries the proxy's address, so
            // ClientIpResolver returns one address for all callers and each per-IP rate
            // limit in RateLimitService becomes a single bucket shared by every client.
            log.warn(
                "app.trusted-proxies (APP_TRUSTED_PROXIES) is empty in the prod profile. " +
                    "If this instance runs behind a reverse proxy, X-Forwarded-For is ignored " +
                    "and every per-IP rate limit collapses into one bucket shared by all clients. " +
                    "Set it to the proxy addresses, or leave it empty only if clients connect directly."
            )
        }
    }
}
