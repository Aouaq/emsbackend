package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * An access token is self-contained, so revoking the refresh family alone used to leave a
 * window of one `app.jwt.expiration` in which a disabled or demoted account kept its
 * authority. These tests pin the behaviour that closes it.
 */
@MobileApiIntegrationTest
class TokenRevocationIntegrationTest : ApiTestSupport() {

    @Test
    fun `disabling an account rejects the access token it already holds`() {
        val admin = adminLogin()
        val email = uniqueEmail("disabled-token")
        registerAndVerify(email)
        val client = login(email)
        val userId = userIdOf(admin.accessToken, email)

        // Still usable before the account is touched.
        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/admin/users/$userId/disable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.disabled").value(true))

        // The same unexpired token is now refused, on reads and on writes alike.
        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"${uniqueKeyValue()}"}""")
        ).andExpect(status().isUnauthorized)

        // Disabling revokes the refresh family too, so refreshing cannot report the
        // reason - the token is simply gone. The client is pushed back to sign-in, and it
        // is the login attempt that names the cause.
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${client.refreshToken}"}""")
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD"}""")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
    }

    @Test
    fun `demoting an admin rejects the admin token it already holds`() {
        val admin = adminLogin()
        val email = uniqueEmail("demoted-admin")
        val created = mockMvc.perform(
            post("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD","role":"ADMIN"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val userId = JsonPath.read<Number>(created, "$.id").toLong()

        val promoted = login(email)
        mockMvc.perform(
            get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, "Bearer ${promoted.accessToken}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/admin/users/$userId/role")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"CLIENT"}""")
        ).andExpect(status().isOk)

        // The token still carries roles=[ADMIN]; only revocation can stop it.
        mockMvc.perform(
            get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, "Bearer ${promoted.accessToken}")
        ).andExpect(status().isUnauthorized)

        // Signing in again yields a token with the role the account actually has now.
        val demoted = login(email)
        mockMvc.perform(
            get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, "Bearer ${demoted.accessToken}")
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${demoted.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("CLIENT"))
    }

    @Test
    fun `signing in again right after a revocation still works`() {
        // `iat` has second precision, so a revocation and an immediately following login
        // can share a timestamp. The fresh token must survive that tie.
        val admin = adminLogin()
        val email = uniqueEmail("requested-logout")
        registerAndVerify(email)
        val client = login(email)

        mockMvc.perform(
            post("/api/me/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isUnauthorized)

        val again = login(email)
        mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${again.accessToken}")
        ).andExpect(status().isOk)

        // Nothing about the admin's own session changed.
        mockMvc.perform(
            get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `api responses do not open a session`() {
        val client = uniqueEmail("stateless").let { email ->
            registerAndVerify(email)
            login(email)
        }
        val result = mockMvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${client.accessToken}")
        ).andExpect(status().isOk).andReturn()

        val cookies = result.response.getHeaders(HttpHeaders.SET_COOKIE)
        kotlin.test.assertTrue(
            cookies.none { it.contains("JSESSIONID") },
            "bearer-token requests must not create an HTTP session, got: $cookies"
        )
        kotlin.test.assertNull(
            result.request.getSession(false),
            "bearer-token requests must not create an HTTP session"
        )
    }

    private fun userIdOf(adminToken: String, email: String): Long {
        val body = mockMvc.perform(
            get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("query", email)
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.read<Number>(body, "$.content[0].id").toLong()
    }

    private fun uniqueKeyValue(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")
}
