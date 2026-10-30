package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class AdminUserIntegrationTest : ApiTestSupport() {

    @Test
    fun `admin creates a new admin who can log in`() {
        val admin = adminLogin()
        val email = uniqueEmail("admin")

        mockMvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","fullName":"Second Admin","role":"ADMIN"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.verified").value(true))

        val tokens = login(email)
        mockMvc.perform(
            get("/api/admin/keys").header("Authorization", "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `duplicate email is rejected`() {
        val admin = adminLogin()
        val email = uniqueEmail("dup")
        createUser(admin.accessToken, email, "ADMIN")

        mockMvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"CLIENT"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `non-admin cannot create users`() {
        val email = uniqueEmail("client")
        registerAndVerify(email)
        val client = login(email)

        mockMvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer ${client.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("forbidden")}","password":"$DEFAULT_PASSWORD","role":"ADMIN"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin promotes a client to admin`() {
        val admin = adminLogin()
        val email = uniqueEmail("promote")
        val created = createUser(admin.accessToken, email, "CLIENT")
        val userId = JsonPath.read<Int>(created, "$.id")

        mockMvc.perform(
            patch("/api/admin/users/$userId/role")
                .header("Authorization", "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))

        val promoted = login(email)
        val users = mockMvc.perform(
            get("/api/admin/users").header("Authorization", "Bearer ${promoted.accessToken}")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(users.contains(email))
    }

    private fun createUser(adminToken: String, email: String, role: String): String =
        mockMvc.perform(
            post("/api/admin/users")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"$role"}""")
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
}
