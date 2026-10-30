package ma.electricwave.service

import ma.electricwave.domain.AuditLog
import ma.electricwave.repository.AuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditService(private val auditLogRepository: AuditLogRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        eventType: String,
        actorUserId: Long? = null,
        actorEmail: String? = null,
        targetUserId: Long? = null,
        targetEmail: String? = null,
        resource: String? = null,
        ipAddress: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        auditLogRepository.save(
            AuditLog(
                eventType = eventType,
                actorUserId = actorUserId,
                actorEmail = actorEmail?.trim()?.lowercase()?.ifBlank { null },
                targetUserId = targetUserId,
                targetEmail = targetEmail?.trim()?.lowercase()?.ifBlank { null },
                resource = resource,
                ipAddress = ipAddress,
                details = details.ifEmpty { null }
            )
        )
    }
}
