package ma.electricwave.service

import ma.electricwave.security.JwtProperties
import ma.electricwave.util.sha256Hex
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64

@Service
class RefreshTokenService(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties
) {

    private val random = SecureRandom()

    fun issue(userId: Long): String {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        redisTemplate.opsForValue().set(key(token), userId.toString(), jwtProperties.refreshExpiration)
        return token
    }

    fun consume(token: String): Long? {
        val value = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        return value.toLongOrNull()
    }

    fun revoke(token: String) {
        redisTemplate.delete(key(token))
    }

    private fun key(token: String): String = "refresh:${sha256Hex(token)}"
}
