package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import ma.electricwave.domain.AlarmKind
import ma.electricwave.dto.AdminAlarmResponse
import ma.electricwave.dto.AdminAlarmSummaryResponse
import ma.electricwave.dto.AdminAlarmThresholdResponse
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.dto.PageResponse
import ma.electricwave.security.JwtService
import ma.electricwave.service.AdminAlarmService
import ma.electricwave.service.AlarmStreamService
import ma.electricwave.service.MeService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Admin - Alarms", description = "Admin-only fleet alarms and per-site alarm thresholds")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/alarms")
class AdminAlarmController(
    private val adminAlarmService: AdminAlarmService,
    private val streams: AlarmStreamService
) {

    @Operation(
        summary = "List alarms across every site",
        description = "Newest first. Filter by open/cleared state, alarm kind and resource. " +
            "Page size is capped at 100. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Alarms returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping
    fun list(
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) kind: AlarmKind?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<AdminAlarmResponse> =
        adminAlarmService.list(active, kind, resource, page, size)

    @Operation(
        summary = "Get fleet alarm totals",
        description = "Open and total counts plus whether this instance is polling sites and when " +
            "it last finished a sweep. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Summary returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping("/summary")
    fun summary(): AdminAlarmSummaryResponse = adminAlarmService.summary()

    @Operation(
        summary = "Stream fleet alarm changes",
        description = "Server-Sent Events. Emits `ready` on connect, `alarm` when any " +
            "registered site is raised and `cleared` when one resolves, with comment " +
            "heartbeats in between. History is not replayed - read it from the list " +
            "endpoint. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Stream opened"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@AuthenticationPrincipal jwt: Jwt, response: HttpServletResponse): SseEmitter {
        response.setHeader("X-Accel-Buffering", "no")
        response.setHeader("Cache-Control", "no-cache, no-transform")
        return streams.open(userId(jwt))
    }

    @Operation(
        summary = "List per-site alarm thresholds",
        description = "Every registered site with its effective limits. `usingDefaults` is true when " +
            "no site-specific row exists. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Thresholds returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping("/thresholds")
    fun thresholds(): List<AdminAlarmThresholdResponse> = adminAlarmService.thresholds()

    @Operation(
        summary = "Set one site's alarm thresholds",
        description = "A null limit disables that check; it is not the same as zero. Audited. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Thresholds saved"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @PutMapping("/thresholds/{accessKeyId}")
    fun updateThresholds(
        @PathVariable accessKeyId: Long,
        @Valid @RequestBody request: AlarmThresholdRequest,
        authentication: Authentication
    ): AdminAlarmThresholdResponse =
        adminAlarmService.updateThresholds(accessKeyId, request, authentication.name)

    @Operation(
        summary = "Reset one site to the application defaults",
        description = "Deletes the site-specific threshold row so the configured defaults apply again. " +
            "Audited when a row existed. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Thresholds reset"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @DeleteMapping("/thresholds/{accessKeyId}")
    fun resetThresholds(
        @PathVariable accessKeyId: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        adminAlarmService.resetThresholds(accessKeyId, authentication.name)
        return ResponseEntity.noContent().build()
    }

    private fun userId(jwt: Jwt): Long =
        MeService.userIdFromClaim(jwt.getClaim<Any>(JwtService.USER_ID_CLAIM))
}
