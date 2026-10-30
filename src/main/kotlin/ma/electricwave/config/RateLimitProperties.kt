package ma.electricwave.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val loginIpPerMinute: Int = 10,
    val loginEmailPerMinute: Int = 5,
    val registerIpPerHour: Int = 5,
    val verifyIpPer10Minutes: Int = 20,
    val resendEmailPer10Minutes: Int = 3,
    val resendIpPerHour: Int = 10,
    val refreshIpPerMinute: Int = 30,
    val googleIpPer10Minutes: Int = 10
)
