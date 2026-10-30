package ma.electricwave

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.exception.ApiException
import ma.electricwave.service.RateLimitService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MobileApiIntegrationTest
class RateLimitIntegrationTest : ApiTestSupport() {

    @Autowired
    private lateinit var rateLimitService: RateLimitService

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `blocks requests above the limit`() {
        val scope = "test:${UUID.randomUUID()}"

        repeat(3) {
            rateLimitService.check(scope, "client", 3, Duration.ofMinutes(1))
        }

        val ex = assertFailsWith<ApiException> {
            rateLimitService.check(scope, "client", 3, Duration.ofMinutes(1))
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
    }

    @Test
    fun `does nothing when disabled`() {
        val disabled = RateLimitService(redisTemplate, RateLimitProperties(enabled = false))
        val scope = "test:${UUID.randomUUID()}"

        repeat(100) {
            disabled.check(scope, "client", 1, Duration.ofMinutes(1))
        }
    }
}
