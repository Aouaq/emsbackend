package ma.electricwave.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun electricWaveOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("ElectricWave API")
                .description(
                    """
                    REST API for the ElectricWave platform.

                    Authentication flow:
                    1. Register with one or more access keys (POST /api/auth/register).
                    2. Verify the 6-digit code sent by email (POST /api/auth/verify); use
                       POST /api/auth/verify/resend to request a new code.
                    3. Log in (POST /api/auth/login), use native Google sign-in
                       (POST /api/auth/oauth2/google) or complete the browser Google flow to obtain
                       a JWT plus a refresh token.
                    4. Rotate tokens with POST /api/auth/refresh and revoke the refresh token with
                       POST /api/auth/logout.
                    5. Send the JWT as `Authorization: Bearer <token>` on protected calls.

                    Access to /api/resources/{resource} is granted only when the caller is an
                    ADMIN or owns an active access key whose `resource` matches the path segment.
                    """.trimIndent()
                )
                .version("0.0.1")
        )
        .servers(listOf(Server().url("/").description("Current server")))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access JWT issued by /api/auth/login, /api/auth/refresh or the Google sign-in flows")
            )
        )

    @Bean
    fun electricWaveApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("electricwave-api")
        .pathsToMatch("/api/**")
        .build()
}
