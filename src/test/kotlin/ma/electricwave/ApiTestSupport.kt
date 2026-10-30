package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

abstract class ApiTestSupport {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    protected fun uniqueEmail(prefix: String = "test"): String =
        "$prefix-${UUID.randomUUID()}@example.com"

    protected fun register(
        email: String,
        password: String = DEFAULT_PASSWORD
    ): String {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isCreated).andReturn()
        return JsonPath.read(result.response.contentAsString, "$.devVerificationCode")
    }

    protected fun verify(email: String, code: String) {
        mockMvc.perform(
            post("/api/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","code":"$code"}""")
        ).andExpect(status().isOk)
    }

    protected fun registerAndVerify(
        email: String,
        password: String = DEFAULT_PASSWORD
    ) {
        val code = register(email, password)
        verify(email, code)
    }

    protected fun login(email: String, password: String = DEFAULT_PASSWORD): TokenPair {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isOk).andReturn()
        val body = result.response.contentAsString
        return TokenPair(
            accessToken = JsonPath.read(body, "$.token"),
            refreshToken = JsonPath.read(body, "$.refreshToken")
        )
    }

    protected fun adminLogin(): TokenPair = login("admin@electricwave.local", "admin123")

    protected data class TokenPair(val accessToken: String, val refreshToken: String)

    companion object {
        const val DEFAULT_PASSWORD = "Passw0rd!"
    }
}
