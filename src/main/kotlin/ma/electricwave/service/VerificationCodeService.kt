package ma.electricwave.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration

@Service
class VerificationCodeService(private val redisTemplate: StringRedisTemplate) {

    private val random = SecureRandom()

    fun issue(email: String, purpose: String = "verify"): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        redisTemplate.opsForValue().set(codeKey(purpose, email), code, CODE_TTL)
        redisTemplate.delete(attemptsKey(purpose, email))
        return code
    }

    fun verify(email: String, code: String, purpose: String = "verify"): Boolean {
        val stored = redisTemplate.opsForValue().get(codeKey(purpose, email)) ?: return false
        val matches = MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            code.toByteArray(Charsets.UTF_8)
        )
        if (matches) {
            redisTemplate.delete(attemptsKey(purpose, email))
            return true
        }

        val key = attemptsKey(purpose, email)
        val attempts = redisTemplate.opsForValue().increment(key) ?: 1L
        redisTemplate.expire(key, CODE_TTL)
        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(codeKey(purpose, email))
            redisTemplate.delete(key)
        }
        return false
    }

    fun clear(email: String, purpose: String = "verify") {
        redisTemplate.delete(codeKey(purpose, email))
        redisTemplate.delete(attemptsKey(purpose, email))
    }

    private fun codeKey(purpose: String, email: String): String = "$purpose:$email"

    private fun attemptsKey(purpose: String, email: String): String = "attempts:$purpose:$email"

    private companion object {
        val CODE_TTL: Duration = Duration.ofMinutes(10)
        const val MAX_ATTEMPTS = 5L
    }
}
