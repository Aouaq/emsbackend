package ma.electricwave.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ClientIpResolver(
    @Value("\${app.trusted-proxies:}") trustedProxies: String
) {

    private val trusted: Set<String> = trustedProxies
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun resolve(request: HttpServletRequest): String {
        val remoteAddress = request.remoteAddr ?: return "unknown"
        if (remoteAddress !in trusted) {
            return remoteAddress
        }
        val forwarded = request.getHeader("X-Forwarded-For") ?: return remoteAddress
        return forwarded
            .split(',')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: remoteAddress
    }
}
