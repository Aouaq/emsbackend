package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "A client linked to an Emoncms account")
data class LinkedClientResponse(
    @field:Schema(description = "Client user identifier", example = "2")
    val userId: Long,
    @field:Schema(description = "Client email", example = "client@example.com")
    val email: String,
    @field:Schema(description = "Client display name", nullable = true)
    val fullName: String?,
    @field:Schema(description = "When the client linked the account (UTC)")
    val linkedAt: Instant
)
