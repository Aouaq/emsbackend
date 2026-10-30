package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Client Resources",
    description = "Resource endpoints gated by the caller's linked access keys. " +
        "ADMIN can access every resource; CLIENT needs an active key whose resource matches the path."
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resources")
class ClientResourceController {

    @Operation(summary = "Read a protected resource", description = "Allowed for ADMIN or clients owning an active key for the resource.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Access granted"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        ApiResponse(responseCode = "403", description = "No linked active access key for this resource")
    )
    @GetMapping("/{resource}")
    @PreAuthorize("@keyAccess.hasAccess(authentication, #resource)")
    fun get(@PathVariable resource: String): Map<String, Any> = mapOf(
        "resource" to resource,
        "message" to "Access granted via your linked access keys"
    )

    @Operation(summary = "Submit to a protected resource", description = "Echoes the payload when access is granted.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Access granted"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        ApiResponse(responseCode = "403", description = "No linked active access key for this resource")
    )
    @PostMapping("/{resource}")
    @PreAuthorize("@keyAccess.hasAccess(authentication, #resource)")
    fun post(@PathVariable resource: String, @RequestBody body: Map<String, Any>): Map<String, Any> = mapOf(
        "resource" to resource,
        "message" to "Access granted via your linked access keys",
        "payload" to body
    )
}
