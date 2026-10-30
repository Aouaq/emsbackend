package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import ma.electricwave.dto.ChangePasswordRequest
import ma.electricwave.dto.ClaimKeyRequest
import ma.electricwave.dto.MeAccessKeyResponse
import ma.electricwave.dto.MeResponse
import ma.electricwave.dto.UpdateProfileRequest
import ma.electricwave.security.ClientIpResolver
import ma.electricwave.security.JwtService
import ma.electricwave.service.MeService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Me", description = "Authenticated account profile and linked Emoncms accounts")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me")
class MeController(
    private val meService: MeService,
    private val clientIpResolver: ClientIpResolver
) {

    @Operation(summary = "Get the authenticated profile")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Profile returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    )
    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse =
        meService.get(userId(jwt))

    @Operation(
        summary = "Update the authenticated profile",
        description = "Updates the display name. A blank name clears it."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Profile updated"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    )
    @PatchMapping
    fun updateProfile(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: UpdateProfileRequest
    ): MeResponse = meService.updateProfile(userId(jwt), request)

    @Operation(
        summary = "Change the account password",
        description = "Requires the current password and revokes every refresh token for the account."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Password changed"),
        ApiResponse(responseCode = "400", description = "Current password incorrect or validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PutMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePasswordRequest,
        http: HttpServletRequest
    ): ResponseEntity<Void> {
        meService.changePassword(userId(jwt), request, clientIpResolver.resolve(http))
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Log out everywhere",
        description = "Revokes every refresh-token family for the authenticated account."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "All sessions revoked"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    )
    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal jwt: Jwt,
        http: HttpServletRequest
    ): ResponseEntity<Void> {
        meService.logoutAll(userId(jwt), clientIpResolver.resolve(http))
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List linked Emoncms accounts")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Linked accounts"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    )
    @GetMapping("/keys")
    fun listKeys(@AuthenticationPrincipal jwt: Jwt): List<MeAccessKeyResponse> =
        meService.listKeys(userId(jwt))

    @Operation(
        summary = "Claim an Emoncms account",
        description = "Links the account identified by the Emoncms read key from its QR code. " +
            "Unknown, malformed and inactive keys all return the same INVALID_KEY error."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account linked"),
        ApiResponse(responseCode = "400", description = "Invalid Emoncms key"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Only CLIENT accounts can claim Emoncms accounts"),
        ApiResponse(responseCode = "409", description = "Account already linked"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/keys")
    fun claimKey(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ClaimKeyRequest,
        http: HttpServletRequest
    ): ResponseEntity<MeAccessKeyResponse> = ResponseEntity.status(201)
        .body(meService.claimKey(userId(jwt), request, clientIpResolver.resolve(http)))

    @Operation(
        summary = "Unlink an Emoncms account",
        description = "Removes only the authenticated client's link to the account."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Link removed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Only CLIENT accounts can unlink Emoncms accounts"),
        ApiResponse(responseCode = "404", description = "Linked account not found")
    )
    @PreAuthorize("hasRole('CLIENT')")
    @DeleteMapping("/keys/{id}")
    fun unlinkKey(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        http: HttpServletRequest
    ): ResponseEntity<Void> {
        meService.unlinkKey(userId(jwt), id, clientIpResolver.resolve(http))
        return ResponseEntity.noContent().build()
    }

    private fun userId(jwt: Jwt): Long =
        MeService.userIdFromClaim(jwt.getClaim<Any>(JwtService.USER_ID_CLAIM))
}
