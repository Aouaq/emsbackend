package ma.electricwave.service

import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.Role
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.ClientResponse
import ma.electricwave.exception.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

@Service
@Transactional(readOnly = true)
class AdminService(
    private val accessKeyRepository: AccessKeyRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createKey(request: AccessKeyRequest): AccessKeyResponse {
        var keyValue = request.keyValue?.trim().orEmpty()
        if (keyValue.isEmpty()) {
            keyValue = generateKeyValue()
            while (accessKeyRepository.existsByKeyValue(keyValue)) {
                keyValue = generateKeyValue()
            }
        } else if (accessKeyRepository.existsByKeyValue(keyValue)) {
            throw ApiException(HttpStatus.CONFLICT, "Access key '$keyValue' already exists")
        }

        return accessKeyRepository.save(
            AccessKey(
                keyValue = keyValue,
                resource = request.resource.trim(),
                description = request.description?.trim()?.ifBlank { null },
                isActive = request.isActive
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

    private fun generateKeyValue(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return "ew_" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun AccessKey.toResponse(): AccessKeyResponse = AccessKeyResponse(
        id = id,
        keyValue = keyValue,
        resource = resource,
        description = description,
        isActive = isActive,
        createdAt = createdAt,
        linkedClients = linkedUsers.filter { it.role == Role.CLIENT }.map { it.email }
    )

    private fun ma.electricwave.domain.User.toClientResponse(): ClientResponse = ClientResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        accessKeys = accessKeys.filter { it.isActive }.map { it.keyValue }
    )

    companion object {
        private val random = SecureRandom()
    }
}
