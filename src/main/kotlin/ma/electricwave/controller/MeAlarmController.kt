package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import ma.electricwave.dto.AlarmFeedResponse
import ma.electricwave.dto.AlarmThresholdRequest
import ma.electricwave.dto.AlarmThresholdResponse
import ma.electricwave.security.JwtService
import ma.electricwave.service.AlarmService
import ma.electricwave.service.AlarmStreamService
import ma.electricwave.service.MeService
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Alarms", description = "Threshold crossings on the caller's linked accounts")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me/alarms")
class MeAlarmController(
    private val alarmService: AlarmService,
    private val streams: AlarmStreamService
) {

    @Operation(
        summary = "List alarms",
        description = "Newest first. Includes when the server last completed a sweep, " +
            "so an empty list can be told apart from a monitor that is not running."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Alarms returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    )
    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "200") limit: Int
    ): AlarmFeedResponse = alarmService.feed(userId(jwt), limit)

    @Operation(summary = "List per-site alarm thresholds")
    @GetMapping("/thresholds")
    fun thresholds(@AuthenticationPrincipal jwt: Jwt): List<AlarmThresholdResponse> =
        alarmService.thresholdsFor(userId(jwt))

    @Operation(
        summary = "Set one site's alarm thresholds",
        description = "A null limit disables that check; it is not the same as zero."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Thresholds saved"),
        ApiResponse(responseCode = "404", description = "No such account for this user")
    )
    @PutMapping("/thresholds/{resource}")
    fun setThresholds(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable resource: String,
        @Valid @RequestBody request: AlarmThresholdRequest
    ): AlarmThresholdResponse =
        alarmService.setThresholds(userId(jwt), resource, request)

    @Operation(
        summary = "Stream alarm changes",
        description = "Server-Sent Events. Emits `ready` on connect, `alarm` when one " +
            "is raised and `cleared` when it resolves, with comment heartbeats in " +
            "between so proxies do not treat a quiet fleet as an idle connection. " +
            "History is not replayed - read it from the list endpoint."
    )
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@AuthenticationPrincipal jwt: Jwt, response: HttpServletResponse): SseEmitter {
        response.setHeader("X-Accel-Buffering", "no")
        response.setHeader("Cache-Control", "no-cache, no-transform")
        return streams.open(userId(jwt))
    }

    private fun userId(jwt: Jwt): Long =
        MeService.userIdFromClaim(jwt.getClaim<Any>(JwtService.USER_ID_CLAIM))
}
