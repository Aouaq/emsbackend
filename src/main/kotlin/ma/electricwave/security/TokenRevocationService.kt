package ma.electricwave.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Server-side revocation for access tokens.
 *
 * An access token is self-contained: once issued it keeps asserting its `roles` and `uid`
 * claims until it expires, so revoking the refresh-token family alone leaves a window
 * (`app.jwt.expiration`, 15m by default) in which a disabled or demoted account still
 * passes authorization. This records the instant a user's tokens were revoked;
 * [RevokedTokenValidator] rejects any token issued at or before it.
 *
 * The marker is stored for exactly one access-token lifetime. After that no unexpired
 * token can predate the revocation, so the entry is redundant and expires on its own.
 */
@Service
class TokenRevocationService(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties
) {

    fun revokeAllForUser(userId: Long) {
        redisTemplate.opsForValue().set(
            key(userId),
            Instant.now().toEpochMilli().toString(),
            jwtProperties.expiration
        )
    }

    /** Epoch millisecond at which this user's tokens were revoked, or null if none were. */
    fun revokedAtEpochMilli(userId: Long): Long? =
        redisTemplate.opsForValue().get(key(userId))?.toLongOrNull()

    private fun key(userId: Long): String = "revoked:user:$userId"
}
