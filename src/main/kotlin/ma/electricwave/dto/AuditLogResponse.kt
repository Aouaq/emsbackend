package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Audit log entry")
data class AuditLogResponse(
    @field:Schema(description = "Audit entry identifier", example = "1")
    val id: Long,
    @field:Schema(description = "Event type", example = "LOGIN_SUCCESS")
    val eventType: String,
    @field:Schema(description = "Actor user identifier", nullable = true)
    val actorUserId: Long?,
    @field:Schema(description = "Actor email", nullable = true)
    val actorEmail: String?,
    @field:Schema(description = "Target user identifier", nullable = true)
    val targetUserId: Long?,
    @field:Schema(description = "Target email", nullable = true)
    val targetEmail: String?,
    @field:Schema(description = "Related resource", nullable = true)
    val resource: String?,
    @field:Schema(description = "Client IP address", nullable = true)
    val ipAddress: String?,
    @field:Schema(description = "Additional event details", nullable = true)
    val details: Map<String, Any?>?,
    @field:Schema(description = "Event timestamp (UTC)")
    val createdAt: Instant
)
