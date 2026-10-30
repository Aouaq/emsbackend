package ma.electricwave.security

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.nio.charset.StandardCharsets
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    @field:NotBlank
    @field:Size(min = 32, message = "app.jwt.secret must be at least 32 characters")
    val secret: String,
    val issuer: String = "electricwave",
    val expiration: Duration = Duration.ofMinutes(15),
    val refreshExpiration: Duration = Duration.ofDays(30)
) {
    init {
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "app.jwt.secret must be at least 32 bytes"
        }
    }
}
