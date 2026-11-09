package ma.electricwave.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import ma.electricwave.domain.User
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Component
class JwtService(private val jwtProperties: JwtProperties) {

    companion object {
        const val ROLES_CLAIM = "roles"
        const val USER_ID_CLAIM = "uid"

        /**
         * Issued-at in epoch milliseconds.
         *
         * The standard `iat` is a NumericDate and carries only seconds, which is too
         * coarse to order a token against a revocation that happens in the same second -
         * and that is the common case, since an admin disabling an account acts moments
         * after the token was minted. [RevokedTokenValidator] uses this instead.
         */
        const val ISSUED_AT_MS_CLAIM = "iat_ms"
    }

    private val jwk = OctetSequenceKey.Builder(
        jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
    ).build()

    private val jwkSource: JWKSource<SecurityContext> =
        JWKSource { jwkSelector, _ -> jwkSelector.select(JWKSet(jwk)) }

    private val encoder = NimbusJwtEncoder(jwkSource)

    fun issue(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .subject(user.email)
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.expiration))
            .id(UUID.randomUUID().toString())
            .claim(ROLES_CLAIM, listOf(user.role.name))
            .claim(USER_ID_CLAIM, user.id)
            .claim(ISSUED_AT_MS_CLAIM, now.toEpochMilli())
            .build()
        val parameters = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        return encoder.encode(parameters).tokenValue
    }
}
