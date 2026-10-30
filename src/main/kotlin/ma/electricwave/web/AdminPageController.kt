package ma.electricwave.web

import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.service.AdminService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/admin")
class AdminPageController(private val adminService: AdminService) {

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
        redirectAttributes: RedirectAttributes
    ): String {
        if (bindingResult.hasErrors()) {
            return "admin/key-form"
        }
        return try {
            val response = adminService.createKey(request)
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

    @PostMapping("/keys/{id}/delete")
    fun deleteKey(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String =
        try {
            adminService.deleteKey(id)
            redirectAttributes.addFlashAttribute("message", "Access key deleted.")
            "redirect:/admin/dashboard"
        } catch (ex: ApiException) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                ex.message ?: "Unable to delete access key"
            )
            "redirect:/admin/dashboard"
        }

    @GetMapping("/clients")
    fun clients(model: Model): String {
        model.addAttribute("title", "Client Users")
        model.addAttribute("clients", adminService.listClients())
        return "admin/clients"
    }
}
