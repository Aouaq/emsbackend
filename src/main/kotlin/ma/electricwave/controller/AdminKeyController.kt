package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.AccessKeyUpdateRequest
import ma.electricwave.dto.ClientResponse
import ma.electricwave.dto.LinkedClientResponse
import ma.electricwave.dto.ReplaceEmoncmsKeyRequest
import ma.electricwave.service.AdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Access Keys", description = "Admin-only management of Emoncms accounts and client links")
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
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "409", description = "The key or resource is already registered")
    )
    @PostMapping("/keys")
    fun createKey(
        @Valid @RequestBody request: AccessKeyRequest,
        authentication: Authentication
    ): ResponseEntity<AccessKeyResponse> =
        ResponseEntity.status(201).body(adminService.createKey(request, authentication.name))

    @Operation(
        summary = "List all registered Emoncms accounts",
        description = "Returns every account with its status, resource, key hint and linked-client count. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Access keys with linked clients"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping("/keys")
    fun listKeys(): List<AccessKeyResponse> = adminService.listKeys()

    @Operation(summary = "Get a registered Emoncms account")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @GetMapping("/keys/{id}")
    fun getKey(@PathVariable id: Long): AccessKeyResponse = adminService.getKey(id)

    @Operation(
        summary = "Update an Emoncms account",
        description = "Updates resource, description and active status. Deactivation immediately blocks " +
            "gateway access while keeping existing links."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account updated"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found"),
        ApiResponse(responseCode = "409", description = "Resource already registered")
    )
    @PutMapping("/keys/{id}")
    fun updateKey(
        @PathVariable id: Long,
        @Valid @RequestBody request: AccessKeyUpdateRequest,
        authentication: Authentication
    ): AccessKeyResponse = adminService.updateKey(id, request, authentication.name)

    @Operation(
        summary = "Replace the Emoncms read key",
        description = "Replaces the stored key and invalidates the old claim code. Existing client links " +
            "are kept and the gateway cache for the resource is evicted."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Key replaced"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found"),
        ApiResponse(responseCode = "409", description = "The new key is already registered")
    )
    @PutMapping("/keys/{id}/emoncms-key")
    fun replaceEmoncmsKey(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReplaceEmoncmsKeyRequest,
        authentication: Authentication
    ): AccessKeyResponse = adminService.replaceEmoncmsKey(id, request, authentication.name)

    @Operation(summary = "List clients linked to an Emoncms account")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Linked clients"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @GetMapping("/keys/{id}/clients")
    fun listLinkedClients(@PathVariable id: Long): List<LinkedClientResponse> =
        adminService.listLinkedClients(id)

    @Operation(summary = "Unlink a client from an Emoncms account")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Link removed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key or link not found")
    )
    @DeleteMapping("/keys/{id}/clients/{userId}")
    fun unlinkClient(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        adminService.unlinkClient(id, userId, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Delete a registered Emoncms account",
        description = "Permanently deletes the account. Client links are removed automatically, so they " +
            "immediately lose access to the resource. Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Access key deleted"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin"),
        ApiResponse(responseCode = "404", description = "Access key not found")
    )
    @DeleteMapping("/keys/{id}")
    fun deleteKey(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        adminService.deleteKey(id, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "List client users",
        description = "Returns all CLIENT accounts with their verification status and linked resources. " +
            "Requires ROLE_ADMIN."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Client accounts"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping("/clients")
    fun listClients(): List<ClientResponse> = adminService.listClients()
}
