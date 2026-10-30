package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNull

@MobileApiIntegrationTest
class PasswordResetIntegrationTest : ApiTestSupport() {

    @Test
    fun `forgot password is neutral for unknown emails`() {
        val body = mockMvc.perform(
            post("/api/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("unknown")}"}""")
        ).andExpect(status().isAccepted)
            .andReturn().response.contentAsString

        assertNull(JsonPath.read(body, "$.devResetCode"))
    }

    @Test
    fun `forgot password does not issue a code for unverified accounts`() {
        val email = uniqueEmail("unverified-reset")
        register(email)

        val body = mockMvc.perform(
            post("/api/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isAccepted)
            .andReturn().response.contentAsString

        assertNull(JsonPath.read(body, "$.devResetCode"))
    }

    @Test
    fun `resets the password and revokes refresh tokens`() {
        val email = uniqueEmail("reset")
        registerAndVerify(email)
        val tokens = login(email)
        val code = forgot(email)
        val newPassword = "ResetPassw0rd!"

        mockMvc.perform(
            post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","code":"$code","newPassword":"$newPassword"}""")
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

        login(email, newPassword)
    }

    @Test
    fun `rejects invalid and exhausted reset codes`() {
        val email = uniqueEmail("badreset")
        registerAndVerify(email)
        val code = forgot(email)
        val wrong = if (code == "000000") "111111" else "000000"

        repeat(5) {
            mockMvc.perform(
                post("/api/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","code":"$wrong","newPassword":"AnotherPassw0rd!"}""")
            ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))
        }

        mockMvc.perform(
            post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","code":"$code","newPassword":"AnotherPassw0rd!"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CODE"))

        mockMvc.perform(
            post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("missing")}","code":"123456","newPassword":"AnotherPassw0rd!"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CODE"))
    }

    private fun forgot(email: String): String {
        val body = mockMvc.perform(
            post("/api/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isAccepted)
            .andReturn().response.contentAsString
        return JsonPath.read(body, "$.devResetCode")
    }
}
