package ma.electricwave.web

import jakarta.validation.Valid
import ma.electricwave.domain.Role
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.ReplaceEmoncmsKeyRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.service.AdminAuditService
import ma.electricwave.service.AdminService
import ma.electricwave.service.AdminUserService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/admin")
class AdminPageController(
    private val adminService: AdminService,
    private val adminUserService: AdminUserService,
    private val adminAuditService: AdminAuditService
) {

    @GetMapping
    fun home(): String = "redirect:/admin/dashboard"

    @GetMapping("/login")
    fun loginPage(model: Model): String {
        model.addAttribute("title", "Admin Login")
        return "admin/login"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model): String {
        val keys = adminService.listKeys()
        model.addAttribute("title", "Dashboard")
        model.addAttribute("keys", keys)
        model.addAttribute("stats", adminService.stats())
        model.addAttribute("totalKeys", keys.size)
        model.addAttribute("activeKeys", keys.count { it.isActive })
        return "admin/dashboard"
    }

    @GetMapping("/keys/new")
    fun keyForm(model: Model): String {
        model.addAttribute("title", "Add Access Key")
        model.addAttribute("request", AccessKeyRequest())
        return "admin/key-form"
    }

    @PostMapping("/keys")
    fun createKey(
        @Valid @ModelAttribute("request") request: AccessKeyRequest,
        bindingResult: BindingResult,
        model: Model,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        if (bindingResult.hasErrors()) {
            return "admin/key-form"
        }
        return try {
            val response = adminService.createKey(request, authentication.name)
            redirectAttributes.addFlashAttribute(
                "message",
                "Access key '${response.resource}' created successfully."
            )
            "redirect:/admin/dashboard"
        } catch (ex: ApiException) {
            model.addAttribute("errorMessage", ex.message ?: "Unable to create access key")
            "admin/key-form"
        }
    }

    @PostMapping("/keys/{id}/toggle")
    fun toggleKey(
        @PathVariable id: Long,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        val key = adminService.toggleKeyActive(id, authentication.name)
        redirectAttributes.addFlashAttribute(
            "message",
            "Access key '${key.resource}' is now ${if (key.isActive) "active" else "inactive"}."
        )
        "redirect:/admin/dashboard"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute(
            "errorMessage",
            ex.message ?: "Unable to update access key"
        )
        "redirect:/admin/dashboard"
    }

    @PostMapping("/keys/{id}/replace")
    fun replaceKey(
        @PathVariable id: Long,
        @RequestParam emoncmsKey: String,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminService.replaceEmoncmsKey(
            id,
            ReplaceEmoncmsKeyRequest(emoncmsKey),
            authentication.name
        )
        redirectAttributes.addFlashAttribute("message", "Emoncms key replaced.")
        "redirect:/admin/dashboard"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute(
            "errorMessage",
            ex.message ?: "Unable to replace the Emoncms key"
        )
        "redirect:/admin/dashboard"
    }

    @PostMapping("/keys/{id}/delete")
    fun deleteKey(
        @PathVariable id: Long,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminService.deleteKey(id, authentication.name)
        redirectAttributes.addFlashAttribute("message", "Access key deleted.")
        "redirect:/admin/dashboard"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute(
            "errorMessage",
            ex.message ?: "Unable to delete access key"
        )
        "redirect:/admin/dashboard"
    }

    @GetMapping("/users")
    fun users(model: Model): String {
        model.addAttribute("title", "Users")
        model.addAttribute("users", adminUserService.search(null, 0, 100).content)
        return "admin/users"
    }

    @PostMapping("/users/{id}/role")
    fun updateRole(
        @PathVariable id: Long,
        @RequestParam role: Role,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminUserService.updateRole(id, role, authentication.name)
        redirectAttributes.addFlashAttribute("message", "Role updated.")
        "redirect:/admin/users"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Unable to update role")
        "redirect:/admin/users"
    }

    @PostMapping("/users/{id}/disable")
    fun disableUser(
        @PathVariable id: Long,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminUserService.disable(id, authentication.name)
        redirectAttributes.addFlashAttribute("message", "User disabled and sessions revoked.")
        "redirect:/admin/users"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Unable to disable user")
        "redirect:/admin/users"
    }

    @PostMapping("/users/{id}/enable")
    fun enableUser(
        @PathVariable id: Long,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminUserService.enable(id, authentication.name)
        redirectAttributes.addFlashAttribute("message", "User enabled.")
        "redirect:/admin/users"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Unable to enable user")
        "redirect:/admin/users"
    }

    @PostMapping("/users/{id}/revoke-sessions")
    fun revokeSessions(
        @PathVariable id: Long,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = try {
        adminUserService.revokeSessions(id, authentication.name)
        redirectAttributes.addFlashAttribute("message", "Sessions revoked.")
        "redirect:/admin/users"
    } catch (ex: ApiException) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Unable to revoke sessions")
        "redirect:/admin/users"
    }

    @GetMapping("/clients")
    fun clients(model: Model): String {
        model.addAttribute("title", "Client Users")
        model.addAttribute("clients", adminService.listClients())
        return "admin/clients"
    }

    @GetMapping("/audit")
    fun audit(
        @RequestParam(defaultValue = "0") page: Int,
        model: Model
    ): String {
        model.addAttribute("title", "Audit Log")
        model.addAttribute("audit", adminAuditService.list(null, page, 20))
        return "admin/audit"
    }
}
