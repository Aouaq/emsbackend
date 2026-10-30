package ma.electricwave.exception

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    message: String,
    val code: String = defaultCode(status),
    val retryAfterSeconds: Long? = null
) : RuntimeException(message) {

    companion object {
        fun defaultCode(status: HttpStatus): String = when (status) {
            HttpStatus.BAD_REQUEST -> "VALIDATION_FAILED"
            HttpStatus.UNAUTHORIZED -> "UNAUTHORIZED"
            HttpStatus.FORBIDDEN -> "FORBIDDEN"
            HttpStatus.NOT_FOUND -> "NOT_FOUND"
            HttpStatus.CONFLICT -> "CONFLICT"
            HttpStatus.TOO_MANY_REQUESTS -> "RATE_LIMITED"
            HttpStatus.BAD_GATEWAY -> "UPSTREAM_ERROR"
            HttpStatus.GATEWAY_TIMEOUT -> "UPSTREAM_TIMEOUT"
            HttpStatus.SERVICE_UNAVAILABLE -> "NOT_CONFIGURED"
            else -> "INTERNAL_ERROR"
        }
    }
}
