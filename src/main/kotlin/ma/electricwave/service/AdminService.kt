package ma.electricwave.service

import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.Role
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.ClientResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.util.EmoncmsKey
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class AdminService(
    private val accessKeyRepository: AccessKeyRepository,
    private val userRepository: UserRepository,
    private val cryptoService: CryptoService
) {

    @Transactional
    fun createKey(request: AccessKeyRequest): AccessKeyResponse {
        val emoncmsKey = EmoncmsKey.normalize(request.emoncmsKey)
        if (!EmoncmsKey.isValid(emoncmsKey)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "The Emoncms key must be 16 to 64 letters or digits"
            )
        }
        val keyHash = EmoncmsKey.hash(emoncmsKey)
        if (accessKeyRepository.existsByKeyHash(keyHash)) {
            throw ApiException(HttpStatus.CONFLICT, "This Emoncms key is already registered")
        }

        val resource = request.resource.trim()
        if (accessKeyRepository.existsByResource(resource)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "An account for resource '$resource' already exists"
            )
        }

        return accessKeyRepository.save(
            AccessKey(
                resource = resource,
                description = request.description?.trim()?.ifBlank { null },
                keyHash = keyHash,
                keyHint = EmoncmsKey.hint(emoncmsKey),
                emoncmsApiKeyEnc = cryptoService.encrypt(emoncmsKey),
                isActive = request.isActive,
                updatedAt = Instant.now()
            )
        ).toResponse()
    }

    fun listKeys(): List<AccessKeyResponse> =
        accessKeyRepository.findAllWithUsers().map { it.toResponse() }

    @Transactional
    fun deleteKey(id: Long) {
        val key = accessKeyRepository.findById(id).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "Access key $id not found")
        }
        accessKeyRepository.delete(key)
    }

    fun listClients(): List<ClientResponse> =
        userRepository.findAllByRoleOrderById(Role.CLIENT).map { it.toClientResponse() }

    private fun AccessKey.toResponse(): AccessKeyResponse = AccessKeyResponse(
        id = id,
        resource = resource,
        description = description,
        isActive = isActive,
        keyHint = keyHint,
        emoncmsKeyConfigured = emoncmsApiKeyEnc.isNotEmpty(),
        linkedClients = linkedUsers.count { it.role == Role.CLIENT },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ma.electricwave.domain.User.toClientResponse(): ClientResponse = ClientResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        accessKeys = accessKeys.filter { it.isActive }.map { it.resource }
    )
}
