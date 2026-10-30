package ma.electricwave.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
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
                    REST API for the ElectricWave Flutter application.

                    Authentication:
                    1. Register a client account with POST /api/auth/register (no Emoncms key).
                    2. Verify the emailed 6-digit code with POST /api/auth/verify.
                    3. Log in with POST /api/auth/login, refresh with POST /api/auth/refresh, or use
                       native Google sign-in with POST /api/auth/oauth2/google.
                    4. Send the JWT as `Authorization: Bearer <token>` on protected calls.

                    Emoncms accounts are claimed from the read key embedded in the QR code or deep
                    link via POST /api/me/keys. The backend stores only a hash and an encrypted copy
                    of the key and never returns the plaintext.

                    Feed access is limited to GET /api/gateway/{resource}/feed/list.json,
                    /feed/data.json and /feed/timevalue.json. Only the documented query parameters
                    are forwarded; client-supplied apikey parameters are rejected.
                    """.trimIndent()
                )
                .version("1.0.0")
        )
        .servers(listOf(Server().url("/").description("Current server")))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access JWT issued by /api/auth/login, /api/auth/refresh or Google sign-in")
            )
        )

    @Bean
    fun problemDetailSchemaCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val components = openApi.components ?: Components().also { openApi.components = it }
        val schemas = components.schemas ?: mutableMapOf()
        components.schemas = schemas
        schemas["ProblemDetail"] = ObjectSchema()
            .description("RFC 7807 problem details returned for API errors")
            .addProperty("type", StringSchema().example("about:blank"))
            .addProperty("title", StringSchema().example("Bad Request"))
            .addProperty("status", io.swagger.v3.oas.models.media.IntegerSchema().example(400))
            .addProperty("detail", StringSchema().example("Request validation failed"))
            .addProperty("instance", StringSchema().example("/api/auth/login"))
            .addProperty("code", StringSchema().example("VALIDATION_FAILED"))
            .addProperty("traceId", StringSchema().example("6f1a0c5e2b7d4a91"))
    }

    @Bean
    fun electricWaveApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("electricwave-api")
        .pathsToMatch("/api/**")
        .build()
}
