package ma.electricwave.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestSizeLimitFilter(
    @Value("\${app.http.max-request-bytes:1048576}") private val maxRequestBytes: Long,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.contentLengthLong > maxRequestBytes) {
            response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
            response.contentType = "application/problem+json"
            objectMapper.writeValue(
                response.outputStream,
                mapOf(
                    "type" to "about:blank",
                    "title" to "Payload Too Large",
                    "status" to HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "detail" to "Request body is too large",
                    "code" to "VALIDATION_FAILED"
                )
            )
            return
        }
        filterChain.doFilter(request, response)
    }
}
