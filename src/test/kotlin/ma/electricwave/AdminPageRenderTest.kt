package ma.electricwave

import ma.electricwave.repository.AlarmThresholdRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class AdminPageRenderTest : ApiTestSupport() {

    @Autowired
    private lateinit var alarmThresholdRepository: AlarmThresholdRepository

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

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `renders the alarms page`() {
        mockMvc.perform(get("/admin/alarms"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Fleet overview")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Per-site thresholds")))
    }

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `saves and resets thresholds from the alarms page`() {
        val resource = "page-${UUID.randomUUID().toString().substring(0, 8)}"
        val id = createAccessKey(resource, UUID.randomUUID().toString().replace("-", ""))

        // A value the form cannot parse is reported as a flash error rather than saved
        // as some accidental number.
        mockMvc.perform(
            post("/admin/alarms/thresholds/$id")
                .with(csrf())
                .param("maxPower", "not-a-number")
                .param("minVoltage", "")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/alarms"))
            .andExpect(flash().attributeExists("errorMessage"))
        assertTrue(alarmThresholdRepository.findById(id).isEmpty)

        // A blank min-voltage disables that check; it must be stored as null, not zero.
        mockMvc.perform(
            post("/admin/alarms/thresholds/$id")
                .with(csrf())
                .param("maxPower", "150000")
                .param("minVoltage", "")
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/alarms"))
            .andExpect(flash().attributeExists("message"))

        val saved = alarmThresholdRepository.findById(id).orElseThrow()
        assertEquals(150000.0, saved.maxPower)
        assertNull(saved.minVoltage)

        mockMvc.perform(get("/admin/alarms"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("150000")))

        mockMvc.perform(
            post("/admin/alarms/thresholds/$id/reset").with(csrf())
        ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/alarms"))
            .andExpect(flash().attributeExists("message"))

        assertTrue(alarmThresholdRepository.findById(id).isEmpty)
    }
}
