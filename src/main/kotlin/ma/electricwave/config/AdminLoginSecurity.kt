package ma.electricwave.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.UserRepository
import ma.electricwave.security.ClientIpResolver
import ma.electricwave.service.AuditService
import ma.electricwave.service.RateLimitService
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Closes the gap where a disabled (or no-longer-admin) account could still complete the
 * `/admin/login` form flow: `CustomUserDetailsService` intentionally does not flag the
 * Spring `UserDetails` as disabled (that would move the disabled-check to *before*
 * credential verification for the API login path too, contradicting the documented
 * "checked after credential verification" behaviour in AuthService). Instead, this
 * handler re-checks the account immediately after Spring Security accepts the
 * credentials, and kills the session if the account turns out to be disabled or is no
 * longer an admin.
 */
@Component
class AdminLoginSuccessHandler(
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    private val clientIpResolver: ClientIpResolver
) : AuthenticationSuccessHandler {

    private val delegate = SavedRequestAwareAuthenticationSuccessHandler().apply {
        setDefaultTargetUrl("/admin/dashboard")
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val email = authentication.name.trim().lowercase()
        val ip = clientIpResolver.resolve(request)
        val user = userRepository.findByEmail(email).orElse(null)

        if (user == null || user.disabledAt != null || !user.isAdmin()) {
            SecurityContextHolder.clearContext()
            request.getSession(false)?.invalidate()
            auditService.record(
                "LOGIN_FAILURE",
                actorEmail = email,
                ipAddress = ip,
                details = mapOf("reason" to "disabled", "flow" to "admin_form")
            )
            response.sendRedirect("${request.contextPath}/admin/login?error")
            return
        }

        auditService.record(
            "LOGIN_SUCCESS",
            actorUserId = user.id,
            actorEmail = email,
            ipAddress = ip,
            details = mapOf("flow" to "admin_form")
        )
        delegate.onAuthenticationSuccess(request, response, authentication)
    }
}

/** Audits admin form-login failures the same way the API login path already does. */
@Component
class AdminLoginFailureHandler(
    private val auditService: AuditService,
    private val clientIpResolver: ClientIpResolver
) : AuthenticationFailureHandler {

    private val delegate = SimpleUrlAuthenticationFailureHandler("/admin/login?error")

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val email = request.getParameter("username")?.trim()?.lowercase()
        auditService.record(
            "LOGIN_FAILURE",
            actorEmail = email,
            ipAddress = clientIpResolver.resolve(request),
            details = mapOf("reason" to "bad_credentials", "flow" to "admin_form")
        )
        delegate.onAuthenticationFailure(request, response, exception)
    }
}

/**
 * Applies the same per-IP rate limit the API login endpoint uses to the `/admin/login`
 * form submission, which previously had none at all despite protecting the most
 * powerful account in the system.
 */
@Component
class AdminLoginRateLimitFilter(
    private val rateLimitService: RateLimitService,
    private val rateLimitProperties: RateLimitProperties,
    private val clientIpResolver: ClientIpResolver
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method == "POST" && request.servletPath == "/admin/login") {
            val ip = clientIpResolver.resolve(request)
            try {
                rateLimitService.check(
                    "admin-login:ip",
                    ip,
                    rateLimitProperties.loginIpPerMinute,
                    Duration.ofMinutes(1)
                )
            } catch (ex: ApiException) {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                ex.retryAfterSeconds?.let { response.setHeader("Retry-After", it.toString()) }
                response.contentType = "text/plain;charset=UTF-8"
                response.writer.write("Too many login attempts. Please try again later.")
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}

/**
 * [AdminLoginRateLimitFilter] is wired explicitly into the Spring Security chain (via
 * `addFilterBefore` in [SecurityConfig]) so it only ever runs there. Without this,
 * Spring Boot would *also* auto-register it as a plain servlet filter on every request
 * (since it's a `Filter` bean), running it twice per admin-login attempt and halving
 * the effective rate limit.
 */
@Configuration
class AdminLoginRateLimitFilterRegistrationConfig {
    @Bean
    fun adminLoginRateLimitFilterRegistration(
        filter: AdminLoginRateLimitFilter
    ): FilterRegistrationBean<AdminLoginRateLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
