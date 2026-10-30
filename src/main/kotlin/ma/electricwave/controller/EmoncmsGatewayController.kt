package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import ma.electricwave.security.ClientIpResolver
import ma.electricwave.security.JwtService
import ma.electricwave.service.EmoncmsGatewayService
import ma.electricwave.service.GatewayResponse
import ma.electricwave.service.MeService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Gateway", description = "Read-only, whitelisted Emoncms feed access for linked accounts")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/gateway")
class EmoncmsGatewayController(
    private val gatewayService: EmoncmsGatewayService,
    private val clientIpResolver: ClientIpResolver
) {

    @Operation(summary = "List feeds", description = "Proxies Emoncms /feed/list.json for a linked account.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Feed list returned"),
        ApiResponse(responseCode = "400", description = "Unsupported query parameter"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "No access to this resource"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        ApiResponse(responseCode = "502", description = "Emoncms returned an error"),
        ApiResponse(responseCode = "503", description = "The stored Emoncms key cannot be decrypted"),
        ApiResponse(responseCode = "504", description = "Emoncms timed out")
    )
    @GetMapping("/{resource}/feed/list.json")
    fun listFeeds(
        @PathVariable resource: String,
        @Parameter(hidden = true) @RequestParam params: Map<String, String>,
        @AuthenticationPrincipal jwt: Jwt,
        http: HttpServletRequest
    ): ResponseEntity<ByteArray> = respond(
        gatewayService.fetch(
            userId(jwt),
            resource,
            "/feed/list.json",
            params,
            clientIpResolver.resolve(http)
        )
    )

    @Operation(
        summary = "Read feed data",
        description = "Proxies Emoncms /feed/data.json. Requires id, start and end; accepts interval and skipmissing."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Feed data returned"),
        ApiResponse(responseCode = "400", description = "Invalid or unsupported query parameters"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "No access to this resource"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        ApiResponse(responseCode = "502", description = "Emoncms returned an error"),
        ApiResponse(responseCode = "503", description = "The stored Emoncms key cannot be decrypted"),
        ApiResponse(responseCode = "504", description = "Emoncms timed out")
    )
    @Parameters(
        Parameter(name = "id", description = "Emoncms feed id (positive)", required = true, `in` = ParameterIn.QUERY),
        Parameter(name = "start", description = "Start timestamp (Unix seconds)", required = true, `in` = ParameterIn.QUERY),
        Parameter(name = "end", description = "End timestamp (Unix seconds)", required = true, `in` = ParameterIn.QUERY),
        Parameter(name = "interval", description = "Optional aggregation interval in seconds", `in` = ParameterIn.QUERY),
        Parameter(name = "skipmissing", description = "Optional 0/1 flag to skip missing values", `in` = ParameterIn.QUERY)
    )
    @GetMapping("/{resource}/feed/data.json")
    fun feedData(
        @PathVariable resource: String,
        @Parameter(hidden = true) @RequestParam params: Map<String, String>,
        @AuthenticationPrincipal jwt: Jwt,
        http: HttpServletRequest
    ): ResponseEntity<ByteArray> = respond(
        gatewayService.fetch(
            userId(jwt),
            resource,
            "/feed/data.json",
            params,
            clientIpResolver.resolve(http)
        )
    )

    @Operation(
        summary = "Read the latest feed value",
        description = "Proxies Emoncms /feed/timevalue.json for a single feed id."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Feed value returned"),
        ApiResponse(responseCode = "400", description = "Invalid or unsupported query parameters"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "No access to this resource"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        ApiResponse(responseCode = "502", description = "Emoncms returned an error"),
        ApiResponse(responseCode = "503", description = "The stored Emoncms key cannot be decrypted"),
        ApiResponse(responseCode = "504", description = "Emoncms timed out")
    )
    @Parameters(
        Parameter(name = "id", description = "Emoncms feed id (positive)", required = true, `in` = ParameterIn.QUERY)
    )
    @GetMapping("/{resource}/feed/timevalue.json")
    fun feedTimeValue(
        @PathVariable resource: String,
        @Parameter(hidden = true) @RequestParam params: Map<String, String>,
        @AuthenticationPrincipal jwt: Jwt,
        http: HttpServletRequest
    ): ResponseEntity<ByteArray> = respond(
        gatewayService.fetch(
            userId(jwt),
            resource,
            "/feed/timevalue.json",
            params,
            clientIpResolver.resolve(http)
        )
    )

    private fun respond(response: GatewayResponse): ResponseEntity<ByteArray> {
        val contentType = runCatching { MediaType.parseMediaType(response.contentType) }
            .getOrDefault(MediaType.APPLICATION_JSON)
        return ResponseEntity.ok().contentType(contentType).body(response.body)
    }

    private fun userId(jwt: Jwt): Long =
        MeService.userIdFromClaim(jwt.getClaim<Any>(JwtService.USER_ID_CLAIM))
}
