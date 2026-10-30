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

@Component
class JwtService(private val jwtProperties: JwtProperties) {

    companion object {
        const val ROLES_CLAIM = "roles"
    }

    private val jwk = OctetSequenceKey.Builder(
        jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
    ).build()

    private val jwkSource: JWKSource<SecurityContext> =
        JWKSource { jwkSelector, _ -> jwkSelector.select(JWKSet(jwk)) }

    private val encoder = NimbusJwtEncoder(jwkSource)

    fun issue(user: User): String = issue(user.email, listOf(user.role.name))

    fun issue(email: String, roles: List<String>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .subject(email)
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.expiration))
            .claim(ROLES_CLAIM, roles)
            .build()
        val parameters = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        return encoder.encode(parameters).tokenValue
    }
}
