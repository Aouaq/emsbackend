package ma.electricwave.web

import ma.electricwave.exception.ApiException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.ModelAndView
import java.util.UUID

/**
 * Renders failures from the admin pages as HTML.
 *
 * `GlobalExceptionHandler` is a `@RestControllerAdvice`, which advises every controller
 * including this package's Thymeleaf ones, so an unexpected error while rendering a page
 * - or a malformed request parameter such as an unknown role - reached the browser as a
 * raw `application/problem+json` body. The page handlers catch [ApiException] themselves,
 * but nothing covered the rest.
 *
 * Ordered ahead of the global advice so that it wins for this package; the global advice
 * still handles everything outside it.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(basePackages = ["ma.electricwave.web"])
class AdminPageExceptionHandler {

    private val log = LoggerFactory.getLogger(AdminPageExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ModelAndView =
        page(ex.status, ex.message ?: "The request could not be completed")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ModelAndView =
        page(HttpStatus.BAD_REQUEST, "Invalid value for '${ex.name}'")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ModelAndView {
        val traceId = UUID.randomUUID().toString()
        log.error("Unhandled admin page exception [traceId={}]", traceId, ex)
        return page(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong while loading this page.",
            traceId
        )
    }

    private fun page(
        status: HttpStatus,
        message: String,
        traceId: String? = null
    ): ModelAndView = ModelAndView("admin/error").apply {
        this.status = status
        addObject("title", status.reasonPhrase)
        addObject("errorMessage", message)
        addObject("traceId", traceId)
    }
}
