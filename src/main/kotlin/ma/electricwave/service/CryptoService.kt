package ma.electricwave.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class CryptoService(
    @Value("\${app.crypto.master-key:}") masterKeyBase64: String,
    environment: Environment
) {

    private val log = LoggerFactory.getLogger(CryptoService::class.java)
    private val secretKey: SecretKeySpec
    private val random = SecureRandom()

    init {
        val decoded = when {
            masterKeyBase64.isBlank() -> {
                if (environment.acceptsProfiles(Profiles.of("dev"))) {
                    log.warn(
                        "APP_CRYPTO_MASTER_KEY is not set; using an ephemeral development key. " +
                            "Stored Emoncms keys will not survive a restart."
                    )
                    ByteArray(KEY_BYTES).also { random.nextBytes(it) }
                } else {
                    throw IllegalStateException("APP_CRYPTO_MASTER_KEY is required outside the dev profile")
                }
            }
            else -> try {
                Base64.getDecoder().decode(masterKeyBase64.trim())
            } catch (ex: IllegalArgumentException) {
                throw IllegalStateException("APP_CRYPTO_MASTER_KEY must be valid base64", ex)
            }
        }
        if (decoded.size != KEY_BYTES) {
            throw IllegalStateException("APP_CRYPTO_MASTER_KEY must decode to exactly 32 bytes")
        }
        secretKey = SecretKeySpec(decoded, "AES")
    }

    fun encrypt(plaintext: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return ByteBuffer.allocate(1 + NONCE_BYTES + ciphertext.size)
            .put(VERSION)
            .put(nonce)
            .put(ciphertext)
            .array()
    }

    fun decrypt(payload: ByteArray): String {
        if (payload.size <= 1 + NONCE_BYTES || payload[0] != VERSION) {
            throw IllegalStateException("Stored Emoncms key payload is invalid")
        }
        val nonce = payload.copyOfRange(1, 1 + NONCE_BYTES)
        val ciphertext = payload.copyOfRange(1 + NONCE_BYTES, payload.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (ex: Exception) {
            throw IllegalStateException("Unable to decrypt stored Emoncms key", ex)
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 0x01
    }
}
