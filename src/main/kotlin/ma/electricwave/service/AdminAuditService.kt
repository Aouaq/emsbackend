package ma.electricwave.service

import ma.electricwave.domain.AuditLog
import ma.electricwave.dto.AuditLogResponse
import ma.electricwave.dto.PageResponse
import ma.electricwave.repository.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class AdminAuditService(private val auditLogRepository: AuditLogRepository) {

    fun list(eventType: String?, page: Int, size: Int): PageResponse<AuditLogResponse> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, PageResponse.MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt")
        )
        val normalized = eventType?.trim()?.uppercase()?.ifBlank { null }
        val result = if (normalized == null) {
            auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            auditLogRepository.findAllByEventTypeOrderByCreatedAtDesc(normalized, pageable)
        }
        return PageResponse.from(result.map { it.toResponse() })
    }

    private fun AuditLog.toResponse(): AuditLogResponse = AuditLogResponse(
        id = id,
        eventType = eventType,
        actorUserId = actorUserId,
        actorEmail = actorEmail,
        targetUserId = targetUserId,
        targetEmail = targetEmail,
        resource = resource,
        ipAddress = ipAddress,
        details = details,
        createdAt = createdAt
    )
}
