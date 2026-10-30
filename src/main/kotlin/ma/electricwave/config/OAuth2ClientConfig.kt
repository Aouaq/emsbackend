package ma.electricwave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType

/**
 * Registers Google as an OAuth2/OIDC client only when both GOOGLE_CLIENT_ID and
 * GOOGLE_CLIENT_SECRET are provided. Without credentials the application still
 * boots and the Google login entry point is simply not wired.
 */
@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {

    @Bean
    @Conditional(GoogleCredentialsPresent::class)
    fun clientRegistrationRepository(
        @Value("\${app.oauth2.google.client-id}") clientId: String,
        @Value("\${app.oauth2.google.client-secret}") clientSecret: String
    ): ClientRegistrationRepository {
        val registration = ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientName("Google")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .issuerUri("https://accounts.google.com")
            .userNameAttributeName("sub")
            .build()
        return InMemoryClientRegistrationRepository(registration)
    }
}

class GoogleCredentialsPresent : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val clientId = context.environment.getProperty("app.oauth2.google.client-id")
        val clientSecret = context.environment.getProperty("app.oauth2.google.client-secret")
        return !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }
}
