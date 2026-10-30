package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import ma.electricwave.dto.AuditLogResponse
import ma.electricwave.dto.PageResponse
import ma.electricwave.service.AdminAuditService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Audit", description = "Admin-only audit log access")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/audit")
class AdminAuditController(private val adminAuditService: AdminAuditService) {

    @Operation(
        summary = "List audit events",
        description = "Returns audit entries newest first, optionally filtered by event type. " +
            "Page size is capped at 100."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Audit entries"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping
    fun list(
        @RequestParam(required = false) eventType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<AuditLogResponse> = adminAuditService.list(eventType, page, size)
}
