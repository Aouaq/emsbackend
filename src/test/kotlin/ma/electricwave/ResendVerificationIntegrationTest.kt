package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@MobileApiIntegrationTest
class ResendVerificationIntegrationTest : ApiTestSupport() {

    @Test
    fun `resend invalidates the previous code`() {
        val email = uniqueEmail()
        val firstCode = register(email)

        val resendResult = mockMvc.perform(
            post("/api/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isOk).andReturn()
        val newCode = JsonPath.read<String>(resendResult.response.contentAsString, "$.devVerificationCode")

        if (newCode != firstCode) {
            mockMvc.perform(
                post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","code":"$firstCode"}""")
            ).andExpect(status().isBadRequest)
        }

        verify(email, newCode)
    }

    @Test
    fun `resend is neutral for unknown emails`() {
        mockMvc.perform(
            post("/api/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("unknown")}"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    fun `resend is a no-op for verified accounts`() {
        val email = uniqueEmail()
        registerAndVerify(email)

        mockMvc.perform(
            post("/api/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isOk)
    }
}
