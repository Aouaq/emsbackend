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

@MobileApiIntegrationTest
class MeIntegrationTest : ApiTestSupport() {

    @Test
    fun `returns and updates the authenticated profile`() {
        val email = uniqueEmail("me")
        registerAndVerify(email)
        val tokens = login(email)

        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("CLIENT"))
            .andExpect(jsonPath("$.verified").value(true))

        mockMvc.perform(
            patch("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Updated Name"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName").value("Updated Name"))
    }

    @Test
    fun `changes the password and revokes every refresh token`() {
        val email = uniqueEmail("password")
        registerAndVerify(email)
        val tokens = login(email)
        val newPassword = "NewPassw0rd!"

        mockMvc.perform(
            put("/api/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"currentPassword":"$DEFAULT_PASSWORD","newPassword":"$newPassword"}"""
                )
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${tokens.refreshToken}"}""")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD"}""")
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))

        login(email, newPassword)
    }

    @Test
    fun `logs out everywhere`() {
        val email = uniqueEmail("logoutall")
        registerAndVerify(email)
        val tokens = login(email)

        mockMvc.perform(
            post("/api/me/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${tokens.refreshToken}"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `claims and unlinks an Emoncms account`() {
        val email = uniqueEmail("claim")
        registerAndVerify(email)
        val tokens = login(email)
        val key = uniqueKey()
        val accessKeyId = createAccessKey(uniqueResource(), key)

        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"not-the-real-key"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_KEY"))

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(accessKeyId))
            .andExpect(jsonPath("$.isActive").value(true))

        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(accessKeyId))

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))

        mockMvc.perform(
            delete("/api/me/keys/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `rejects inactive keys and admin claims`() {
        val email = uniqueEmail("inactive")
        registerAndVerify(email)
        val tokens = login(email)
        val inactiveKey = uniqueKey()
        createAccessKey(uniqueResource(), inactiveKey, active = false)

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$inactiveKey"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_KEY"))

        val admin = adminLogin()
        val activeKey = uniqueKey()
        createAccessKey(uniqueResource(), activeKey)

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$activeKey"}""")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `cannot unlink another client's account`() {
        val firstEmail = uniqueEmail("owner")
        val secondEmail = uniqueEmail("other")
        registerAndVerify(firstEmail)
        registerAndVerify(secondEmail)
        val first = login(firstEmail)
        val second = login(secondEmail)

        val key = uniqueKey()
        val accessKeyId = createAccessKey(uniqueResource(), key)
        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${first.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            delete("/api/me/keys/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${second.accessToken}")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `returns the linked key metadata from the database`() {
        val email = uniqueEmail("metadata")
        registerAndVerify(email)
        val tokens = login(email)
        val resource = uniqueResource()
        val key = uniqueKey()
        val accessKeyId = createAccessKey(resource, key)

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)

        val body = mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk).andReturn().response.contentAsString

        assertEquals(resource, JsonPath.read(body, "$[0].resource"))
        assertEquals(key.take(4), JsonPath.read<String>(body, "$[0].keyHint").take(4))
        assertEquals(accessKeyId, JsonPath.read<Number>(body, "$[0].id").toLong())
    }

    private fun uniqueResource(): String =
        "r-${UUID.randomUUID().toString().substring(0, 8)}"

    private fun uniqueKey(): String =
        UUID.randomUUID().toString().replace("-", "")
}
