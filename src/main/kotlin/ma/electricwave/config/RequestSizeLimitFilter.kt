package ma.electricwave.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.io.IOException

/**
 * Thrown once a request body read exceeds the configured limit. Caught by
 * [RequestSizeLimitFilter] itself; never expected to escape it.
 */
private class PayloadTooLargeException : IOException("Request body exceeds the configured limit")

/**
 * Enforces [maxBytes] on the number of bytes actually read from the body, independent
 * of whether Content-Length is present. A chunked request reports contentLengthLong == -1
 * and would otherwise bypass the Content-Length check entirely.
 */
private class SizeLimitedInputStream(
    private val delegate: ServletInputStream,
    private val maxBytes: Long
) : ServletInputStream() {

    private var total = 0L

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) {
            total++
            if (total > maxBytes) throw PayloadTooLargeException()
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read > 0) {
            total += read
            if (total > maxBytes) throw PayloadTooLargeException()
        }
        return read
    }

    override fun isFinished(): Boolean = delegate.isFinished
    override fun isReady(): Boolean = delegate.isReady
    override fun setReadListener(readListener: ReadListener?) = delegate.setReadListener(readListener)
}

private class SizeLimitedRequest(
    request: HttpServletRequest,
    private val maxBytes: Long
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream = SizeLimitedInputStream(super.getInputStream(), maxBytes)
}

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
        // Fast path: a declared Content-Length over the limit is rejected immediately,
        // without reading any of the body.
        if (request.contentLengthLong > maxRequestBytes) {
            rejectTooLarge(response)
            return
        }

        // Slow path / chunked requests: contentLengthLong is -1 (unknown) for chunked
        // transfer encoding, so wrap the stream to enforce the limit as bytes are
        // actually consumed downstream.
        val wrapped = SizeLimitedRequest(request, maxRequestBytes)
        try {
            filterChain.doFilter(wrapped, response)
        } catch (ex: Exception) {
            // The limit is enforced by the stream either way, but the status code was not:
            // a body read by a message converter surfaces as HttpMessageNotReadableException,
            // a RuntimeException that never matched a `catch (IOException)` and so reached
            // GlobalExceptionHandler as 400 instead of 413. Match on the cause chain
            // rather than on the wrapper type, since each layer wraps differently.
            if (!causedByPayloadTooLarge(ex)) {
                throw ex
            }
            if (!response.isCommitted) rejectTooLarge(response)
        }
    }

    private fun causedByPayloadTooLarge(ex: Throwable): Boolean {
        var current: Throwable? = ex
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is PayloadTooLargeException) return true
            current = current.cause
        }
        return false
    }

    private fun rejectTooLarge(response: HttpServletResponse) {
        if (response.isCommitted) return
        response.reset()
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
    }
}
