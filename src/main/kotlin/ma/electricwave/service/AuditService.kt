package ma.electricwave.service

import ma.electricwave.domain.AuditLog
import ma.electricwave.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditService(private val auditWriter: AuditWriter) {

    private val log = LoggerFactory.getLogger(AuditService::class.java)

    /**
     * Records an audit event, never failing the caller.
     *
     * The write runs in its own transaction so it survives a rollback of the operation
     * being audited, but that also means its failures are independent of that operation:
     * a full disk or a dropped connection would otherwise turn a successful login into a
     * 500. An audit trail that loses an entry is strictly better than an auth endpoint
     * that stops working, so failures are logged at error level and swallowed.
     */
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
        val entry = AuditLog(
            eventType = eventType,
            actorUserId = actorUserId,
            actorEmail = actorEmail?.trim()?.lowercase()?.ifBlank { null },
            targetUserId = targetUserId,
            targetEmail = targetEmail?.trim()?.lowercase()?.ifBlank { null },
            resource = resource,
            ipAddress = ipAddress,
            details = details.ifEmpty { null }
        )
        try {
            auditWriter.write(entry)
        } catch (ex: Exception) {
            log.error(
                "Failed to record audit event {} (actor={}, target={}, resource={})",
                eventType,
                entry.actorEmail ?: entry.actorUserId,
                entry.targetEmail ?: entry.targetUserId,
                resource,
                ex
            )
        }
    }
}

/**
 * Holds the `REQUIRES_NEW` boundary.
 *
 * It lives in its own bean because [AuditService.record] catches the failure: Spring's
 * transaction advice is applied by a proxy, so a self-call would bypass it and the entry
 * would join the caller's transaction and vanish with a rollback.
 */
@Service
class AuditWriter(private val auditLogRepository: AuditLogRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(entry: AuditLog) {
        auditLogRepository.save(entry)
    }
}
