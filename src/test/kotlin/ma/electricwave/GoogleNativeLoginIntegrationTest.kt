package ma.electricwave

import com.jayway.jsonpath.JsonPath
import ma.electricwave.exception.ApiException
import ma.electricwave.security.GoogleTokenVerifier
import ma.electricwave.security.GoogleUser
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class GoogleNativeLoginIntegrationTest : ApiTestSupport() {

    @MockitoBean
    lateinit var googleTokenVerifier: GoogleTokenVerifier

    @Test
    fun `creates a verified client with access keys`() {
        val email = uniqueEmail("google")
        given(googleTokenVerifier.verify("valid-token"))
            .willReturn(GoogleUser(email, "Google User"))

        val result = mockMvc.perform(
            post("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token","accessKeys":["ew_metering_01"]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("CLIENT"))
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val token = JsonPath.read<String>(result.response.contentAsString, "$.token")
        mockMvc.perform(
            get("/api/resources/metering").header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
    }

    @Test
    fun `upgrades an existing unverified local account`() {
        val email = uniqueEmail("upgrade")
        register(email)

        given(googleTokenVerifier.verify("valid-token"))
            .willReturn(GoogleUser(email, "Upgraded User"))

        mockMvc.perform(
            post("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token"}""")
        ).andExpect(status().isOk)

        val tokens = login(email)
        assertTrue(tokens.accessToken.isNotBlank())
    }

    @Test
    fun `rejects invalid Google tokens`() {
        given(googleTokenVerifier.verify("bad-token"))
            .willThrow(ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token"))

        mockMvc.perform(
            post("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"bad-token"}""")
        ).andExpect(status().isUnauthorized)
    }
}
