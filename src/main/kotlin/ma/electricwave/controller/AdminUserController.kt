package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import ma.electricwave.dto.AdminUserCreateRequest
import ma.electricwave.dto.AdminUserResponse
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
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Users", description = "Admin-only provisioning and role management of user accounts")
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
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "409", description = "An account with this email already exists")
    )
    @PostMapping
    fun create(@Valid @RequestBody request: AdminUserCreateRequest): ResponseEntity<AdminUserResponse> =
        ResponseEntity.status(201).body(adminUserService.create(request))

    @Operation(
        summary = "List all users",
        description = "Returns every account with its role, provider, verification status and linked keys. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponse(responseCode = "200", description = "All user accounts")
    @GetMapping
    fun list(): List<AdminUserResponse> = adminUserService.list()

    @Operation(
        summary = "Change a user's role",
        description = "Promotes or demotes an account. The last remaining admin cannot be demoted and admins " +
            "cannot change their own role. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Role updated"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
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
}
