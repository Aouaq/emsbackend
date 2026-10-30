package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import ma.electricwave.dto.AdminStatsResponse
import ma.electricwave.service.AdminService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Stats", description = "Admin-only dashboard statistics")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/stats")
class AdminStatsController(private val adminService: AdminService) {

    @Operation(summary = "Get dashboard statistics")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Statistics returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
        ApiResponse(responseCode = "403", description = "Caller is not an admin")
    )
    @GetMapping
    fun stats(): AdminStatsResponse = adminService.stats()
}
