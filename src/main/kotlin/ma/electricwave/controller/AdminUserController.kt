package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import ma.electricwave.dto.AdminUserCreateRequest
import ma.electricwave.dto.AdminUserResponse
import ma.electricwave.dto.PageResponse
import ma.electricwave.dto.RoleUpdateRequest
import ma.electricwave.service.AdminUserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Users", description = "Admin-only provisioning, search and account lifecycle")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(private val adminUserService: AdminUserService) {

    @Operation(
        summary = "Create a user account",
        description = "Creates a verified local account with the requested role. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account created"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "409", description = "An account with this email already exists")
    )
    @PostMapping
    fun create(
        @Valid @RequestBody request: AdminUserCreateRequest,
        authentication: Authentication
    ): ResponseEntity<AdminUserResponse> = ResponseEntity.status(201)
        .body(adminUserService.create(request, authentication.name))

    @Operation(
        summary = "Search users",
        description = "Returns a page of accounts, optionally filtered by email or display name. " +
            "Page size is capped at 100. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Matching user accounts"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<AdminUserResponse> = adminUserService.search(query, page, size)

    @Operation(
        summary = "Change a user's role",
        description = "Promotes or demotes an account. The last remaining admin cannot be demoted and admins " +
            "cannot change their own role. Promotion to ADMIN removes all Emoncms links. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Role updated"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "409", description = "Last-admin or self-role-change guard triggered")
    )
    @PatchMapping("/{id}/role")
    fun updateRole(
        @PathVariable id: Long,
        @Valid @RequestBody request: RoleUpdateRequest,
        authentication: Authentication
    ): AdminUserResponse = adminUserService.updateRole(id, request.role!!, authentication.name)

    @Operation(
        summary = "Disable a user",
        description = "Disables the account, revokes its refresh tokens and evicts its access cache. " +
            "The last remaining admin cannot be disabled."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account disabled"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "409", description = "Last-admin or self-disable guard triggered")
    )
    @PostMapping("/{id}/disable")
    fun disable(
        @PathVariable id: Long,
        authentication: Authentication
    ): AdminUserResponse = adminUserService.disable(id, authentication.name)

    @Operation(summary = "Enable a user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account enabled"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    @PostMapping("/{id}/enable")
    fun enable(
        @PathVariable id: Long,
        authentication: Authentication
    ): AdminUserResponse = adminUserService.enable(id, authentication.name)

    @Operation(
        summary = "Revoke all sessions",
        description = "Revokes every refresh-token family for the account without disabling it."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Sessions revoked"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    @PostMapping("/{id}/revoke-sessions")
    fun revokeSessions(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        adminUserService.revokeSessions(id, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
