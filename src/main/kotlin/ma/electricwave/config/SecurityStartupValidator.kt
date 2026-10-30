package ma.electricwave.config

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class SecurityStartupValidator(
    private val environment: Environment,
    @Value("\${app.auth.expose-verification-code:false}") private val exposeVerificationCode: Boolean
) : InitializingBean {

    override fun afterPropertiesSet() {
        if (exposeVerificationCode && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw IllegalStateException(
                "app.auth.expose-verification-code must be false in the prod profile"
            )
        }
    }
}
