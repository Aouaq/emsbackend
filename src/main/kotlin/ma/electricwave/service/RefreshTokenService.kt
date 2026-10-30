package ma.electricwave.service

import ma.electricwave.security.JwtProperties
import ma.electricwave.util.sha256Hex
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

sealed interface RefreshConsumeResult {
    data class Active(val userId: Long, val familyId: String) : RefreshConsumeResult
    data class Reused(val userId: Long, val familyId: String) : RefreshConsumeResult
    data object Invalid : RefreshConsumeResult
}

data class RevokedToken(val userId: Long, val familyId: String)

@Service
class RefreshTokenService(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties
) {

    private val random = SecureRandom()

    fun issue(userId: Long, familyId: String? = null): String {
        val family = familyId ?: UUID.randomUUID().toString()
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { random.nextBytes(it) })
        val hash = sha256Hex(token)
        val ttl = jwtProperties.refreshExpiration

        redisTemplate.opsForValue().set(tokenKey(hash), "$userId|$family", ttl)
        redisTemplate.opsForValue().set(familyKey(family), hash, ttl)
        redisTemplate.opsForSet().add(userKey(userId), family)
        redisTemplate.expire(userKey(userId), ttl)
        return token
    }

    fun consume(token: String): RefreshConsumeResult {
        val hash = sha256Hex(token)
        val value = redisTemplate.opsForValue().getAndDelete(tokenKey(hash))
        if (value == null) {
            val used = redisTemplate.opsForValue().get(usedKey(hash))
                ?: return RefreshConsumeResult.Invalid
            val (userId, familyId) = parse(used) ?: return RefreshConsumeResult.Invalid
            revokeFamily(familyId, userId)
            return RefreshConsumeResult.Reused(userId, familyId)
        }

        val (userId, familyId) = parse(value) ?: return RefreshConsumeResult.Invalid
        val currentHash = redisTemplate.opsForValue().get(familyKey(familyId))
        if (currentHash == null) {
            return RefreshConsumeResult.Invalid
        }
        if (currentHash != hash) {
            revokeFamily(familyId, userId)
            return RefreshConsumeResult.Reused(userId, familyId)
        }

        redisTemplate.opsForValue().set(
            usedKey(hash),
            "$userId|$familyId",
            jwtProperties.refreshExpiration
        )
        return RefreshConsumeResult.Active(userId, familyId)
    }

    fun revoke(token: String): RevokedToken? {
        val hash = sha256Hex(token)
        val value = redisTemplate.opsForValue().getAndDelete(tokenKey(hash)) ?: return null
        val (userId, familyId) = parse(value) ?: return null
        revokeFamily(familyId, userId)
        return RevokedToken(userId, familyId)
    }

    fun revokeAllForUser(userId: Long) {
        val families = redisTemplate.opsForSet().members(userKey(userId)).orEmpty()
        families.forEach { revokeFamily(it, userId) }
        redisTemplate.delete(userKey(userId))
    }

    private fun revokeFamily(familyId: String, userId: Long) {
        val currentHash = redisTemplate.opsForValue().get(familyKey(familyId))
        if (currentHash != null) {
            redisTemplate.delete(tokenKey(currentHash))
        }
        redisTemplate.delete(familyKey(familyId))
        redisTemplate.opsForSet().remove(userKey(userId), familyId)
    }

    private fun parse(value: String): Pair<Long, String>? {
        val separator = value.indexOf('|')
        if (separator <= 0) return null
        val userId = value.substring(0, separator).toLongOrNull() ?: return null
        val familyId = value.substring(separator + 1)
        if (familyId.isBlank()) return null
        return userId to familyId
    }

    private fun tokenKey(hash: String): String = "rt:$hash"

    private fun familyKey(familyId: String): String = "rtfam:$familyId"

    private fun usedKey(hash: String): String = "rtused:$hash"

    private fun userKey(userId: Long): String = "rtuser:$userId"
}
