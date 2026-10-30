package ma.electricwave.repository

import ma.electricwave.domain.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AuditLog>

    fun findAllByEventTypeOrderByCreatedAtDesc(
        eventType: String,
        pageable: Pageable
    ): Page<AuditLog>
}
