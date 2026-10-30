package ma.electricwave.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration

@Service
class VerificationCodeService(private val redisTemplate: StringRedisTemplate) {

    private val random = SecureRandom()

    fun issue(email: String): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        redisTemplate.opsForValue().set(key(email), code, Duration.ofMinutes(10))
        return code
    }

    fun verify(email: String, code: String): Boolean {
        val stored = redisTemplate.opsForValue().get(key(email)) ?: return false
        return stored == code
    }

    fun clear(email: String) {
        redisTemplate.delete(key(email))
    }

    private fun key(email: String): String = "verify:$email"
}
