package ma.electricwave.config

import ma.electricwave.security.ApiAccessDeniedHandler
import ma.electricwave.security.ApiAuthenticationEntryPoint
import ma.electricwave.security.JwtProperties
import ma.electricwave.security.RevokedTokenValidator
import ma.electricwave.security.TokenRevocationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class, RateLimitProperties::class)
class SecurityConfig(
    private val userDetailsService: UserDetailsService,
    private val jwtProperties: JwtProperties,
    private val tokenRevocationService: TokenRevocationService,
    private val apiAuthenticationEntryPoint: ApiAuthenticationEntryPoint,
    private val apiAccessDeniedHandler: ApiAccessDeniedHandler,
    private val adminLoginSuccessHandler: AdminLoginSuccessHandler,
    private val adminLoginFailureHandler: AdminLoginFailureHandler,
    private val adminLoginRateLimitFilter: AdminLoginRateLimitFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return ProviderManager(provider)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(
        SecretKeySpec(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    ).build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(jwtProperties.issuer),
                // Disabling or demoting an account cannot un-issue access tokens already
                // in the wild, so the decoder also consults the revocation marker.
                RevokedTokenValidator(tokenRevocationService)
            )
        )
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        }
    }

    /**
     * Cross-origin access to the API, for the Flutter web build.
     *
     * Native apps are unaffected - a browser is the only client that asks. Without this
     * a web build cannot call the API at all: posting JSON triggers a preflight, the
     * preflight goes unanswered, and the request is dropped before reaching a
     * controller, so the server log shows nothing whatsoever.
     *
     * Origins are an allow-list of patterns rather than `*`. Patterns because
     * `flutter run -d chrome` picks a fresh port on each launch, so an exact origin
     * would have to be re-set constantly during development.
     *
     * Credentials are deliberately **not** allowed. This API authenticates with a Bearer
     * header, never a cookie, so nothing needs them - and refusing them is what keeps a
     * port wildcard safe, since a matched origin still cannot ride on an existing
     * session. The list is empty outside dev, so a deployment must name its own origins.
     */
    @Bean
    fun corsConfigurationSource(
        @Value("\${app.cors.allowed-origin-patterns:}") rawPatterns: String
    ): CorsConfigurationSource {
        val patterns = rawPatterns.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val source = UrlBasedCorsConfigurationSource()
        if (patterns.isEmpty()) {
            // Nothing registered, so the filter adds no headers at all and same-origin
            // callers are untouched.
            return source
        }

        val config = CorsConfiguration().apply {
            allowedOriginPatterns = patterns
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type")
            allowCredentials = false
            maxAge = 3600
        }
        source.registerCorsConfiguration("/api/**", config)
        return source
    }

    /**
     * Bearer-token API, stateless.
     *
     * Without an explicit policy the default `IF_REQUIRED` stores a `SecurityContext` in a
     * new `HttpSession` on every authenticated call, so a polling mobile client accumulates
     * sessions for the whole session timeout and an admin's mobile token also mints a
     * cookie that authenticates the admin UI.
     *
     * CSRF is disabled for this chain rather than matcher-excluded on a shared one: the
     * chain's own matcher now draws the boundary, which removes the previous dependency on
     * an absolute-URI prefix that a servlet context path would have silently broken.
     */
    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            // Enabled here so preflight is answered before authorization runs; without
            // it a browser never gets as far as sending the real request.
            .cors { }
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .requestCache { cache -> cache.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/verify",
                        "/api/auth/verify/resend",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/oauth2/google",
                        "/api/auth/password/forgot",
                        "/api/auth/password/reset"
                    ).permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(apiAuthenticationEntryPoint)
                    .accessDeniedHandler(apiAccessDeniedHandler)
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }

        return http.build()
    }

    /**
     * Admin UI, API docs and everything else: session-based form login with CSRF enabled.
     * `/error` stays permitted here because an error dispatch from any request, including
     * one to an API path, is forwarded to it and so lands on this chain.
     */
    @Bean
    @Order(2)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/admin/login",
                        "/error",
                        "/actuator/health/**"
                    ).permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml"
                    ).hasRole("ADMIN")
                    .anyRequest().denyAll()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/admin/login"))
            }
            .formLogin { form ->
                form.loginPage("/admin/login")
                    .successHandler(adminLoginSuccessHandler)
                    .failureHandler(adminLoginFailureHandler)
            }
            .logout { logout ->
                logout.logoutUrl("/admin/logout")
                    .logoutSuccessUrl("/admin/login?logout")
            }
            .addFilterBefore(adminLoginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
