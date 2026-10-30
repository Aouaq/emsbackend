package ma.electricwave.service

import ma.electricwave.exception.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration

data class UpstreamResponse(val body: ByteArray, val contentType: String)

@Component
class EmoncmsClient(
    @Value("\${app.emoncms.base-url}") baseUrl: String
) {

    private val baseUrl = baseUrl.trimEnd('/')

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun get(path: String, params: Map<String, String>, apiKey: String): UpstreamResponse {
        val query = (params + ("apikey" to apiKey))
            .entries
            .joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        val uri = URI.create("$baseUrl$path?$query")
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (ex: HttpTimeoutException) {
            throw ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "The Emoncms server did not respond in time",
                "UPSTREAM_TIMEOUT"
            )
        } catch (ex: IOException) {
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "The Emoncms server could not be reached",
                "UPSTREAM_ERROR"
            )
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "The Emoncms request was interrupted",
                "UPSTREAM_ERROR"
            )
        }

        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "The Emoncms server returned an error",
                "UPSTREAM_ERROR"
            )
        }

        val body = response.body().use { readLimited(it) }
        val contentType = response.headers().firstValue("content-type")
            .orElse("application/json")
        return UpstreamResponse(body, contentType)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) {
                throw ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "The Emoncms response is too large",
                    "UPSTREAM_ERROR"
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    }
}
