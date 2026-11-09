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

        // The header can be attacker-supplied up to the point where our trusted proxy
        // appended its own observed peer address. Walk from the right and skip any
        // entries that are themselves trusted proxies (chained hops); the first
        // non-trusted entry found that way is the one our trusted proxy actually
        // observed on the wire, not one the client could have injected.
        val hops = forwarded
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return hops.lastOrNull { it !in trusted } ?: hops.lastOrNull() ?: remoteAddress
    }
}
