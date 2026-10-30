package ma.electricwave

import ma.electricwave.domain.Role
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.UserRepository
import ma.electricwave.service.AdminUserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(
    properties = [
        "app.admin.email=guard-admin@electricwave.local",
        "app.admin.password=guard-pass-123",
        "app.admin.full-name=Guard Admin"
    ]
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AdminUserGuardIntegrationTest : ApiTestSupport() {

    @Autowired
    private lateinit var adminUserService: AdminUserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `last admin cannot be demoted`() {
        val admin = userRepository.findByEmail("guard-admin@electricwave.local").orElseThrow()

        val ex = assertFailsWith<ApiException> {
            adminUserService.updateRole(admin.id, Role.CLIENT, "someone-else@example.com")
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun `admin cannot change own role`() {
        val admin = userRepository.findByEmail("guard-admin@electricwave.local").orElseThrow()

        val ex = assertFailsWith<ApiException> {
            adminUserService.updateRole(admin.id, Role.CLIENT, admin.email)
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }
}
