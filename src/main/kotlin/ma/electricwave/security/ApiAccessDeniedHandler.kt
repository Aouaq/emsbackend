package ma.electricwave.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiAccessDeniedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = "application/problem+json"
        objectMapper.writeValue(
            response.outputStream,
            mapOf(
                "type" to "about:blank",
                "title" to "Forbidden",
                "status" to HttpStatus.FORBIDDEN.value(),
                "detail" to "Access denied",
                "code" to "FORBIDDEN"
            )
        )
    }
}
