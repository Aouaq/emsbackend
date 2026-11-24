package ma.electricwave

import io.mockk.every
import io.mockk.mockk
import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.LinkedClientProjection
import ma.electricwave.repository.UserRepository
import ma.electricwave.service.AlarmStreamService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audience rule, without an SSE emitter.
 *
 * Sending an event needs a live handler, but which users *should* receive one is a
 * plain set computation and the part that regressed: admins are deliberately not
 * linked to the sites they manage, so a link-only fan-out leaves them deaf.
 */
class AlarmStreamRecipientsTest {

    private val accessKeys = mockk<AccessKeyRepository>()
    private val users = mockk<UserRepository>()
    private val service = AlarmStreamService(accessKeys, users)

    @Test
    fun `fleet alarms reach linked clients and every enabled admin`() {
        every { accessKeys.findLinkedClients(3L) } returns listOf(client(5))
        every { users.findAllByRoleOrderById(Role.ADMIN) } returns listOf(
            admin(1),
            admin(2, disabled = true)
        )

        assertEquals(setOf(1L, 5L), service.recipientsFor(3L))
    }

    @Test
    fun `an admin who also claimed the site is announced once`() {
        every { accessKeys.findLinkedClients(3L) } returns listOf(client(5))
        every { users.findAllByRoleOrderById(Role.ADMIN) } returns listOf(admin(5))

        assertEquals(setOf(5L), service.recipientsFor(3L))
    }

    @Test
    fun `a site nobody claimed and no admin watches has no audience`() {
        every { accessKeys.findLinkedClients(3L) } returns emptyList()
        every { users.findAllByRoleOrderById(Role.ADMIN) } returns emptyList()

        assertTrue(service.recipientsFor(3L).isEmpty())
    }

    private fun client(id: Long) = object : LinkedClientProjection {
        override fun getUserId() = id
        override fun getEmail() = "client$id@example.com"
        override fun getFullName(): String? = null
        override fun getLinkedAt() = Instant.EPOCH
    }

    private fun admin(id: Long, disabled: Boolean = false) = User(
        id = id,
        role = Role.ADMIN,
        disabledAt = if (disabled) Instant.now() else null
    )
}
