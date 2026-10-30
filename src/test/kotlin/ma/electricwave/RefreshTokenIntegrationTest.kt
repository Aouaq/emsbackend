package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class RefreshTokenIntegrationTest : ApiTestSupport() {

    @Test
    fun `login returns access and refresh tokens`() {
        val email = uniqueEmail()
        registerAndVerify(email)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$DEFAULT_PASSWORD"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.expiresIn").value(900))
    }

    @Test
    fun `refresh rotates the token and rejects reuse`() {
        val email = uniqueEmail()
        registerAndVerify(email)
        val first = login(email)

        val refreshed = refresh(first.refreshToken)
        assertTrue(refreshed.accessToken.isNotBlank())
        assertNotEquals(first.refreshToken, refreshed.refreshToken)

        mockMvc.perform(refreshRequest(first.refreshToken))
            .andExpect(status().isUnauthorized)

        val second = refresh(refreshed.refreshToken)
        assertTrue(second.accessToken.isNotBlank())
    }

    @Test
    fun `logout revokes the refresh token`() {
        val email = uniqueEmail()
        registerAndVerify(email)
        val tokens = login(email)

        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${tokens.refreshToken}"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(refreshRequest(tokens.refreshToken))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh rejects unknown tokens`() {
        mockMvc.perform(refreshRequest("not-a-real-token"))
            .andExpect(status().isUnauthorized)
    }

    private fun refresh(refreshToken: String): TokenPair {
        val result = mockMvc.perform(refreshRequest(refreshToken))
            .andExpect(status().isOk)
            .andReturn()
        val body = result.response.contentAsString
        return TokenPair(
            accessToken = JsonPath.read(body, "$.token"),
            refreshToken = JsonPath.read(body, "$.refreshToken")
        )
    }

    private fun refreshRequest(refreshToken: String) =
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"refreshToken":"$refreshToken"}""")
}
