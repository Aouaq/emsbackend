package ma.electricwave.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ProblemDetail> {
        val problem = problem(ex.status, ex.message ?: "Request failed", ex.code)
        val builder = ResponseEntity.status(ex.status)
        ex.retryAfterSeconds?.let { builder.header("Retry-After", it.toString()) }
        return builder.body(problem)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED")
        ex.bindingResult.fieldErrors
            .forEach { problem.setProperty(it.field, it.defaultMessage ?: "invalid value") }
        return problem
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED")

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            "Invalid value for parameter '${ex.name}'",
            "VALIDATION_FAILED"
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Malformed request body", "VALIDATION_FAILED")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Resource not found", "NOT_FOUND")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "The request conflicts with existing data", "CONFLICT")

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Invalid email or password", "INVALID_CREDENTIALS")

    @ExceptionHandler(DisabledException::class)
    fun handleDisabled(ex: DisabledException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "Account is disabled", "ACCOUNT_DISABLED")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Authentication required", "UNAUTHORIZED")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        val traceId = UUID.randomUUID().toString()
        log.error("Unhandled exception [traceId={}]", traceId, ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "INTERNAL_ERROR", traceId)
    }

    private fun problem(
        status: HttpStatus,
        detail: String,
        code: String,
        traceId: String? = null
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.setProperty("code", code)
        if (traceId != null) {
            problem.setProperty("traceId", traceId)
        }
        return problem
    }
}
