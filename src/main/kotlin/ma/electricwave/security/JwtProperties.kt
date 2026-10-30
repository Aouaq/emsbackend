package ma.electricwave.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "electricwave",
    val expiration: Duration = Duration.ofMinutes(30),
    val refreshExpiration: Duration = Duration.ofDays(30)
)
