package ma.electricwave

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class OpenApiExportTest : ApiTestSupport() {

    @Test
    @WithMockUser(username = "admin@electricwave.local", roles = ["ADMIN"])
    fun `exports the OpenAPI document and covers every v1 route`() {
        val body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val output = Path.of("build", "openapi.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, body)

        val paths = JsonPath.read<Map<String, Any>>(body, "$.paths")
        listOf(
            "/api/auth/register",
            "/api/auth/verify",
            "/api/auth/verify/resend",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/auth/oauth2/google",
            "/api/me",
            "/api/me/password",
            "/api/me/logout-all",
            "/api/me/keys",
            "/api/me/keys/{id}",
            "/api/admin/keys",
            "/api/admin/keys/{id}",
            "/api/admin/keys/{id}/emoncms-key",
            "/api/admin/keys/{id}/clients",
            "/api/admin/keys/{id}/clients/{userId}",
            "/api/admin/clients",
            "/api/admin/users",
            "/api/admin/users/{id}/role",
            "/api/admin/users/{id}/disable",
            "/api/admin/users/{id}/enable",
            "/api/admin/users/{id}/revoke-sessions",
            "/api/admin/audit",
            "/api/admin/stats"
        ).forEach { path ->
            assertTrue(paths.containsKey(path), "missing OpenAPI path $path")
        }

        assertFalse(paths.containsKey("/api/resources/{resource}"), "legacy resource proxy documented")
        assertFalse(paths.containsKey("/api/auth/oauth2/google/callback"), "browser OAuth documented")

        val schemas = JsonPath.read<Map<String, Any>>(body, "$.components.schemas")
        assertTrue(schemas.containsKey("ProblemDetail"), "ProblemDetail schema missing")
        val problemDetail = JsonPath.read<Map<String, Any>>(body, "$.components.schemas.ProblemDetail.properties")
        assertTrue(problemDetail.containsKey("code"))
        assertTrue(problemDetail.containsKey("traceId"))
    }
}
