package ma.electricwave.security

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Rejects access tokens issued before their user's tokens were revoked.
 *
 * Runs inside the `JwtDecoder`, so a revoked token fails validation and the resource
 * server answers 401 rather than the request reaching a controller. The reason is
 * deliberately not disclosed here, and a client cannot learn it by refreshing either -
 * the operations that revoke an access token revoke the refresh family with it, so that
 * call returns 401 too. What the client gets is an ordinary "signed out", and the next
 * sign-in attempt names the cause (403 ACCOUNT_DISABLED for a disabled account) or
 * simply succeeds with the account's current role.
 *
 * Ordering uses [JwtService.ISSUED_AT_MS_CLAIM] rather than the standard `iat`, which
 * carries only seconds. A revocation almost always lands in the same second as the token
 * it is meant to kill, so second precision cannot separate the two: comparing one way
 * lets every revocation through, and the other way rejects the fresh token a user gets by
 * signing in again straight afterwards.
 */
class RevokedTokenValidator(
    private val tokenRevocationService: TokenRevocationService
) : OAuth2TokenValidator<Jwt> {

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val userId = userId(token) ?: return failure("Access token has no usable subject id")
        val revokedAtMillis = tokenRevocationService.revokedAtEpochMilli(userId)
            ?: return OAuth2TokenValidatorResult.success()

        val issuedAtMillis = claimAsLong(token, JwtService.ISSUED_AT_MS_CLAIM)
        if (issuedAtMillis != null) {
            // Strictly before: a token minted in the same millisecond as the revocation
            // is kept, so that signing in again immediately afterwards works. Issuing and
            // revoking within one millisecond of each other is not a case worth losing
            // that for, and the disabled-account path is covered regardless, since login
            // refuses a disabled account outright and MeService re-reads disabledAt.
            return if (issuedAtMillis < revokedAtMillis) {
                failure("Access token was revoked")
            } else {
                OAuth2TokenValidatorResult.success()
            }
        }

        // Tokens minted before this claim existed. Their `iat` is truncated to the start
        // of its second, so a same-second revocation is ambiguous; resolve it against the
        // token. These expire within one access-token lifetime of the deployment.
        val issuedAt = token.issuedAt ?: return failure("Access token has no issued-at claim")
        return if (issuedAt.toEpochMilli() <= revokedAtMillis) {
            failure("Access token was revoked")
        } else {
            OAuth2TokenValidatorResult.success()
        }
    }

    private fun userId(token: Jwt): Long? = claimAsLong(token, JwtService.USER_ID_CLAIM)

    private fun claimAsLong(token: Jwt, name: String): Long? =
        when (val value = token.getClaim<Any>(name)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun failure(description: String): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(
            OAuth2Error("invalid_token", description, null)
        )
}
