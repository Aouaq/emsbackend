package ma.electricwave

import ma.electricwave.exception.ApiException
import ma.electricwave.security.GoogleTokenVerifier
import ma.electricwave.security.GoogleUser
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class GoogleNativeLoginIntegrationTest : ApiTestSupport() {

    @MockitoBean
    lateinit var googleTokenVerifier: GoogleTokenVerifier

    @Test
    fun `creates a verified client`() {
        val email = uniqueEmail("google")
        given(googleTokenVerifier.verify("valid-token"))
            .willReturn(GoogleUser(email, "Google User"))

        mockMvc.perform(
            post("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-token"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("CLIENT"))
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
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
