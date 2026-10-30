package ma.electricwave

import ma.electricwave.service.CryptoService
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CryptoServiceTest {

    private val masterKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `encrypts and decrypts a key`() {
        val service = CryptoService(masterKey, devEnvironment())
        val plaintext = "3ddd9a580253f6c9aab6298f754cf0fd"

        val payload = service.encrypt(plaintext)

        assertEquals(0x01.toByte(), payload[0])
        assertNotEquals(plaintext.toByteArray(), payload)
        assertEquals(plaintext, service.decrypt(payload))
    }

    @Test
    fun `uses a fresh nonce for every encryption`() {
        val service = CryptoService(masterKey, devEnvironment())

        val first = service.encrypt("same-key-value-1234")
        val second = service.encrypt("same-key-value-1234")

        assertTrue(first.size > 13)
        assertNotEquals(first.copyOfRange(1, 13).toList(), second.copyOfRange(1, 13).toList())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `rejects a tampered payload`() {
        val service = CryptoService(masterKey, devEnvironment())
        val payload = service.encrypt("3ddd9a580253f6c9aab6298f754cf0fd")
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()

        assertFailsWith<IllegalStateException> { service.decrypt(payload) }
    }

    @Test
    fun `rejects an invalid master key length`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

        assertFailsWith<IllegalStateException> {
            CryptoService(shortKey, devEnvironment())
        }
    }

    @Test
    fun `requires a master key outside dev`() {
        assertFailsWith<IllegalStateException> {
            CryptoService("", MockEnvironment().apply { setActiveProfiles("prod") })
        }
    }

    private fun devEnvironment(): MockEnvironment =
        MockEnvironment().apply { setActiveProfiles("dev") }
}
