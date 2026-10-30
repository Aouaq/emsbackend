package ma.electricwave

import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@MobileApiIntegrationTest
class AdminPageRenderTest : ApiTestSupport() {

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `renders the dashboard`() {
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Access Keys Dashboard")))
    }

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `renders the users page`() {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Users")))
    }

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `renders the audit page`() {
        mockMvc.perform(get("/admin/audit"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Audit Log")))
    }
}
