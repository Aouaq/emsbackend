package ma.electricwave.controller

import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.ClientResponse
import ma.electricwave.service.AdminService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Access Keys", description = "Admin-only management of access keys and client accounts")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
class AdminKeyController(private val adminService: AdminService) {

    @Operation(
        summary = "Register an Emoncms account",
        description = "Stores a hashed and encrypted copy of the account's Emoncms read key. " +
            "The plaintext key is never returned. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account registered"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "409", description = "The key or resource is already registered")
    )
    @PostMapping("/keys")
    fun createKey(@Valid @RequestBody request: AccessKeyRequest): ResponseEntity<AccessKeyResponse> =
        ResponseEntity.status(201).body(adminService.createKey(request))

    @Operation(
        summary = "List all registered Emoncms accounts",
        description = "Returns every account with its status, resource, key hint and linked-client count. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponse(responseCode = "200", description = "Access keys with linked clients")
    @GetMapping("/keys")
    fun listKeys(): List<AccessKeyResponse> = adminService.listKeys()

    @Operation(
        summary = "Delete a registered Emoncms account",
        description = "Permanently deletes the account. Client links are removed automatically, so they " +
            "immediately lose access to the resource. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Access key deleted"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @DeleteMapping("/keys/{id}")
    fun deleteKey(@PathVariable id: Long): ResponseEntity<Void> {
        adminService.deleteKey(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "List client users",
        description = "Returns all CLIENT accounts with their verification status and linked resources. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponse(responseCode = "200", description = "Client accounts")
    @GetMapping("/clients")
    fun listClients(): List<ClientResponse> = adminService.listClients()
}
