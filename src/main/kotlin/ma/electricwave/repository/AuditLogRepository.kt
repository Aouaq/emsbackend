package ma.electricwave.repository

import ma.electricwave.domain.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long>
