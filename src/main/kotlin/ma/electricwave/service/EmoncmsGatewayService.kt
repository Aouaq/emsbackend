package ma.electricwave.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import ma.electricwave.config.RateLimitProperties
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class GatewayResponse(val body: ByteArray, val contentType: String)

@Service
class EmoncmsGatewayService(
    private val accessKeyRepository: AccessKeyRepository,
    private val accountAccessService: AccountAccessService,
    private val cryptoService: CryptoService,
    private val emoncmsClient: EmoncmsClient,
    private val rateLimitService: RateLimitService,
    private val rateLimitProperties: RateLimitProperties,
    private val auditService: AuditService
) {

    private val cache: Cache<String, GatewayResponse> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMillis(750))
        .build()

    private val inFlight = ConcurrentHashMap<String, CompletableFuture<GatewayResponse>>()

    fun fetch(
        userId: Long,
        resource: String,
        path: String,
        params: Map<String, String>,
        clientIp: String
    ): GatewayResponse {
        rateLimitService.check(
            "gateway:user",
            userId.toString(),
            rateLimitProperties.gatewayPerUserPerMinute,
            Duration.ofMinutes(1)
        )

        validateResource(resource)
        val validated = validateParams(path, params)

        if (!accountAccessService.canAccess(userId, resource)) {
            auditService.record(
                "GATEWAY_DENIED",
                actorUserId = userId,
                resource = resource,
                ipAddress = clientIp,
                details = mapOf("path" to path)
            )
            throw ApiException(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN")
        }

        val accessKey = accessKeyRepository.findAccessible(userId, resource).orElse(null)
        if (accessKey == null) {
            auditService.record(
                "GATEWAY_DENIED",
                actorUserId = userId,
                resource = resource,
                ipAddress = clientIp,
                details = mapOf("path" to path)
            )
            throw ApiException(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN")
        }

        val emoncmsKey = try {
            cryptoService.decrypt(accessKey.emoncmsApiKeyEnc)
        } catch (ex: Exception) {
            throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "This Emoncms account is not configured",
                "NOT_CONFIGURED"
            )
        }

        val cacheKey = cacheKey(resource, path, validated)
        cache.getIfPresent(cacheKey)?.let { return it }

        val future = CompletableFuture<GatewayResponse>()
        val existing = inFlight.putIfAbsent(cacheKey, future)
        if (existing != null) {
            return await(existing)
        }

        return try {
            val upstream = emoncmsClient.get(path, validated, emoncmsKey)
            val response = GatewayResponse(upstream.body, upstream.contentType)
            cache.put(cacheKey, response)
            future.complete(response)
            response
        } catch (ex: Throwable) {
            future.completeExceptionally(ex)
            throw ex
        } finally {
            inFlight.remove(cacheKey, future)
        }
    }

    fun evictResource(resource: String) {
        cache.asMap().keys.removeIf { it.startsWith("$resource|") }
    }

    private fun await(future: CompletableFuture<GatewayResponse>): GatewayResponse = try {
        future.get(15, TimeUnit.SECONDS)
    } catch (ex: ExecutionException) {
        val cause = ex.cause
        if (cause is RuntimeException) throw cause
        throw ApiException(
            HttpStatus.BAD_GATEWAY,
            "The Emoncms request failed",
            "UPSTREAM_ERROR"
        )
    } catch (ex: TimeoutException) {
        throw ApiException(
            HttpStatus.GATEWAY_TIMEOUT,
            "The Emoncms server did not respond in time",
            "UPSTREAM_TIMEOUT"
        )
    } catch (ex: InterruptedException) {
        Thread.currentThread().interrupt()
        throw ApiException(
            HttpStatus.BAD_GATEWAY,
            "The Emoncms request was interrupted",
            "UPSTREAM_ERROR"
        )
    }

    private fun validateResource(resource: String) {
        if (!RESOURCE_PATTERN.matches(resource)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid resource", "VALIDATION_FAILED")
        }
    }

    private fun validateParams(path: String, params: Map<String, String>): Map<String, String> {
        val allowed = when (path) {
            "/feed/list.json" -> emptySet()
            "/feed/timevalue.json" -> setOf("id")
            "/feed/data.json" -> setOf("id", "start", "end", "interval", "skipmissing")
            else -> throw ApiException(HttpStatus.NOT_FOUND, "Resource not found", "NOT_FOUND")
        }

        params.keys.firstOrNull { it !in allowed }?.let { name ->
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Unsupported query parameter '$name'",
                "VALIDATION_FAILED"
            )
        }

        if (path != "/feed/list.json") {
            val id = requiredLong(params, "id")
            if (id <= 0) {
                throw ApiException(HttpStatus.BAD_REQUEST, "Invalid feed id", "VALIDATION_FAILED")
            }
        }

        if (path == "/feed/data.json") {
            val start = requiredLong(params, "start")
            val end = requiredLong(params, "end")
            if (start < 0 || end <= start) {
                throw ApiException(
                    HttpStatus.BAD_REQUEST,
                    "end must be greater than start",
                    "VALIDATION_FAILED"
                )
            }
            if (end - start > MAX_RANGE_SECONDS) {
                throw ApiException(
                    HttpStatus.BAD_REQUEST,
                    "The requested range must not exceed 400 days",
                    "VALIDATION_FAILED"
                )
            }
            params["interval"]?.let {
                val interval = it.toIntOrNull()
                if (interval == null || interval <= 0) {
                    throw ApiException(
                        HttpStatus.BAD_REQUEST,
                        "interval must be a positive integer",
                        "VALIDATION_FAILED"
                    )
                }
            }
            params["skipmissing"]?.let {
                if (it != "0" && it != "1") {
                    throw ApiException(
                        HttpStatus.BAD_REQUEST,
                        "skipmissing must be 0 or 1",
                        "VALIDATION_FAILED"
                    )
                }
            }
        }

        return params
    }

    private fun requiredLong(params: Map<String, String>, name: String): Long {
        val raw = params[name] ?: throw ApiException(
            HttpStatus.BAD_REQUEST,
            "Missing required query parameter '$name'",
            "VALIDATION_FAILED"
        )
        return raw.toLongOrNull() ?: throw ApiException(
            HttpStatus.BAD_REQUEST,
            "Invalid value for query parameter '$name'",
            "VALIDATION_FAILED"
        )
    }

    private fun cacheKey(resource: String, path: String, params: Map<String, String>): String {
        val sorted = params.toSortedMap().entries
            .joinToString("&") { "${it.key}=${it.value}" }
        return "$resource|$path|$sorted"
    }

    private companion object {
        val RESOURCE_PATTERN = Regex("^[a-z0-9][a-z0-9-]{0,99}$")
        const val MAX_RANGE_SECONDS = 400L * 24 * 60 * 60
    }
}
