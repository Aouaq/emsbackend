package ma.electricwave.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader("WWW-Authenticate", "Bearer")
        response.contentType = "application/problem+json"
        objectMapper.writeValue(
            response.outputStream,
            mapOf(
                "type" to "about:blank",
                "title" to "Unauthorized",
                "status" to HttpStatus.UNAUTHORIZED.value(),
                "detail" to "Authentication required",
                "code" to "UNAUTHORIZED"
            )
        )
    }
}
