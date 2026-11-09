package ma.electricwave.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Adds a Content-Security-Policy to the admin pages.
 *
 * Scoped to the admin UI rather than applied through the security chain, because that
 * chain also serves Swagger UI, which relies on inline scripts a useful policy has to
 * forbid. The admin templates escape every value and carry no inline script of their own,
 * so only inline styles need allowing.
 *
 * Transport security is intentionally absent: the deployment still terminates plain HTTP
 * (see docs/DECISIONS.md), so HSTS would be inert. Add it with TLS.
 */
@Component
class AdminSecurityHeadersFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.servletPath.startsWith("/admin")) {
            response.setHeader("Content-Security-Policy", POLICY)
            response.setHeader("Referrer-Policy", "same-origin")
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        val POLICY = listOf(
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "object-src 'none'"
        ).joinToString("; ")
    }
}
