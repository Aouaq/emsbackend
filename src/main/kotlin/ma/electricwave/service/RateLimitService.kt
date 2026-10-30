package ma.electricwave.service

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.exception.ApiException
import ma.electricwave.util.sha256Hex
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RateLimitProperties
) {

    private val incrementScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent()
        )
        setResultType(Long::class.java)
    }

    fun check(scope: String, identifier: String, limit: Int, window: Duration) {
        if (!properties.enabled) return

        val windowMillis = window.toMillis()
        val bucket = System.currentTimeMillis() / windowMillis
        val key = "rl:$scope:${sha256Hex(identifier)}:$bucket"

        val count = redisTemplate.execute(
            incrementScript,
            listOf(key),
            windowMillis.toString()
        ) ?: 0L

        if (count > limit) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later."
            )
        }
    }
}
