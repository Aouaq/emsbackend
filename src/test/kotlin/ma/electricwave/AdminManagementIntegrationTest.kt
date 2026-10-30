package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class AdminManagementIntegrationTest : ApiTestSupport() {

    @Test
    fun `creates updates replaces and deletes an access key`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val resource = uniqueResource()
        val key = uniqueKey()

        val created = mockMvc.perform(
            post("/api/admin/keys")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"resource":"$resource","description":"Initial","emoncmsKey":"$key","isActive":true}"""
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.resource").value(resource))
            .andExpect(jsonPath("$.emoncmsKeyConfigured").value(true))
            .andExpect(jsonPath("$.linkedClients").value(0))
            .andReturn().response.contentAsString
        val id = JsonPath.read<Number>(created, "$.id").toLong()

        val updatedResource = uniqueResource()
        mockMvc.perform(
            put("/api/admin/keys/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"resource":"$updatedResource","description":"Updated","isActive":false}"""
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value(updatedResource))
            .andExpect(jsonPath("$.isActive").value(false))
            .andExpect(jsonPath("$.description").value("Updated"))

        val newKey = uniqueKey()
        mockMvc.perform(
            put("/api/admin/keys/$id/emoncms-key")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"emoncmsKey":"$newKey"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.keyHint").value(newKey.take(4) + "\u2026"))

        mockMvc.perform(
            get("/api/admin/keys/$id").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value(updatedResource))

        mockMvc.perform(
            delete("/api/admin/keys/$id").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/admin/keys/$id").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `normalizes whitespace around Emoncms keys`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val resource = uniqueResource()
        val key = uniqueKey()

        mockMvc.perform(
            post("/api/admin/keys")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resource":"$resource","emoncmsKey":"  $key\n"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.keyHint").value(key.take(4) + "\u2026"))

        val email = uniqueEmail("trimmed")
        registerAndVerify(email)
        val client = login(email)
        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"\t$key \n"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.resource").value(resource))
    }

    @Test
    fun `lists and unlinks linked clients`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val email = uniqueEmail("linked")
        registerAndVerify(email)
        val client = login(email)
        val resource = uniqueResource()
        val key = uniqueKey()
        val id = createAccessKey(resource, key)

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)

        val linked = mockMvc.perform(
            get("/api/admin/keys/$id/clients").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value(email))
            .andReturn().response.contentAsString
        val userId = JsonPath.read<Number>(linked, "$[0].userId").toLong()

        mockMvc.perform(
            delete("/api/admin/keys/$id/clients/$userId")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(
            delete("/api/admin/keys/$id/clients/$userId")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `disables enables and revokes user sessions`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val email = uniqueEmail("lifecycle")
        val created = mockMvc.perform(
            post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"CLIENT"}""")
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val userId = JsonPath.read<Number>(created, "$.id").toLong()

        val tokens = login(email)

        mockMvc.perform(
            post("/api/admin/users/$userId/disable").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.disabled").value(true))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD"}""")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${tokens.refreshToken}"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/admin/users/$userId/enable").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.disabled").value(false))

        val newTokens = login(email)
        mockMvc.perform(
            post("/api/admin/users/$userId/revoke-sessions").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${newTokens.refreshToken}"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `promoting a client removes its links`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val email = uniqueEmail("promoted")
        val created = mockMvc.perform(
            post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"CLIENT"}""")
        ).andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val userId = JsonPath.read<Number>(created, "$.id").toLong()

        val client = login(email)
        val resource = uniqueResource()
        val key = uniqueKey()
        createAccessKey(resource, key)
        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            patch("/api/admin/users/$userId/role")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `searches users with pagination and returns statistics`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val prefix = "search-${UUID.randomUUID().toString().substring(0, 8)}"
        val email = "$prefix@example.com"

        mockMvc.perform(
            post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"CLIENT"}""")
        ).andExpect(status().isCreated)

        val body = mockMvc.perform(
            get("/api/admin/users")
                .param("query", prefix)
                .param("page", "0")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.content[0].email").value(email))
            .andReturn().response.contentAsString

        assertTrue(JsonPath.read<Number>(body, "$.totalElements").toLong() >= 1)

        mockMvc.perform(
            get("/api/admin/users")
                .param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(100))

        mockMvc.perform(
            get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.users.total").isNumber)
            .andExpect(jsonPath("$.accessKeys.total").isNumber)
            .andExpect(jsonPath("$.links").isNumber)
    }

    @Test
    fun `audit log is paginated filtered and admin-only`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val email = uniqueEmail("audit")
        mockMvc.perform(
            post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"CLIENT"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/admin/audit")
                .param("eventType", "USER_CREATED")
                .param("page", "0")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].eventType").value("USER_CREATED"))
            .andExpect(jsonPath("$.size").value(1))

        mockMvc.perform(
            get("/api/admin/audit")
                .param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(100))

        val clientEmail = uniqueEmail("audit-client")
        registerAndVerify(clientEmail)
        val client = login(clientEmail)
        mockMvc.perform(
            get("/api/admin/audit").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isForbidden)
    }

    private fun uniqueResource(): String =
        "a-${UUID.randomUUID().toString().substring(0, 8)}"

    private fun uniqueKey(): String =
        UUID.randomUUID().toString().replace("-", "")
}
