package ma.electricwave.service

import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.Role
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.AccessKeyUpdateRequest
import ma.electricwave.dto.AdminStatsResponse
import ma.electricwave.dto.ClientResponse
import ma.electricwave.dto.LinkedClientResponse
import ma.electricwave.dto.ReplaceEmoncmsKeyRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.util.EmoncmsKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class AdminService(
    private val accessKeyRepository: AccessKeyRepository,
    private val userRepository: UserRepository,
    private val cryptoService: CryptoService,
    private val accountAccessService: AccountAccessService,
    private val gatewayService: EmoncmsGatewayService,
    private val auditService: AuditService,
    private val emoncmsClient: EmoncmsClient,
    @Value("\${app.emoncms.validate-on-save:false}") private val validateOnSave: Boolean
) {

    @Transactional
    fun createKey(request: AccessKeyRequest, actorEmail: String? = null): AccessKeyResponse {
        val emoncmsKey = EmoncmsKey.normalize(request.emoncmsKey)
        validateEmoncmsKey(emoncmsKey)
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

        val saved = accessKeyRepository.save(
            AccessKey(
                resource = resource,
                description = request.description?.trim()?.ifBlank { null },
                keyHash = keyHash,
                keyHint = EmoncmsKey.hint(emoncmsKey),
                emoncmsApiKeyEnc = cryptoService.encrypt(emoncmsKey),
                isActive = request.isActive,
                updatedAt = Instant.now()
            )
        )
        auditService.record(
            "KEY_CREATED",
            actorEmail = actorEmail,
            resource = resource,
            details = mapOf("isActive" to request.isActive)
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateKey(
        id: Long,
        request: AccessKeyUpdateRequest,
        actorEmail: String? = null
    ): AccessKeyResponse {
        val key = findKey(id)
        val oldResource = key.resource
        val newResource = request.resource.trim()
        if (newResource != oldResource && accessKeyRepository.existsByResource(newResource)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "An account for resource '$newResource' already exists"
            )
        }

        key.resource = newResource
        key.description = request.description?.trim()?.ifBlank { null }
        key.isActive = request.isActive
        key.updatedAt = Instant.now()
        val saved = accessKeyRepository.save(key)
        accountAccessService.evictResource(oldResource)
        accountAccessService.evictResource(newResource)
        gatewayService.evictResource(oldResource)
        gatewayService.evictResource(newResource)
        auditService.record(
            "KEY_UPDATED",
            actorEmail = actorEmail,
            resource = newResource,
            details = mapOf(
                "oldResource" to oldResource,
                "isActive" to request.isActive
            )
        )
        return saved.toResponse()
    }

    @Transactional
    fun replaceEmoncmsKey(
        id: Long,
        request: ReplaceEmoncmsKeyRequest,
        actorEmail: String? = null
    ): AccessKeyResponse {
        val key = findKey(id)
        val emoncmsKey = EmoncmsKey.normalize(request.emoncmsKey)
        validateEmoncmsKey(emoncmsKey)
        val keyHash = EmoncmsKey.hash(emoncmsKey)
        accessKeyRepository.findByKeyHash(keyHash).ifPresent { existing ->
            if (existing.id != id) {
                throw ApiException(HttpStatus.CONFLICT, "This Emoncms key is already registered")
            }
        }

        key.keyHash = keyHash
        key.keyHint = EmoncmsKey.hint(emoncmsKey)
        key.emoncmsApiKeyEnc = cryptoService.encrypt(emoncmsKey)
        key.updatedAt = Instant.now()
        val saved = accessKeyRepository.save(key)
        accountAccessService.evictResource(key.resource)
        gatewayService.evictResource(key.resource)
        auditService.record(
            "EMONCMS_KEY_REPLACED",
            actorEmail = actorEmail,
            resource = key.resource
        )
        return saved.toResponse()
    }

    fun listKeys(): List<AccessKeyResponse> =
        accessKeyRepository.findAllWithUsers().map { it.toResponse() }

    fun getKey(id: Long): AccessKeyResponse = findKey(id).toResponse()

    fun listLinkedClients(id: Long): List<LinkedClientResponse> {
        findKey(id)
        return accessKeyRepository.findLinkedClients(id).map {
            LinkedClientResponse(
                userId = it.getUserId(),
                email = it.getEmail(),
                fullName = it.getFullName(),
                linkedAt = it.getLinkedAt()
            )
        }
    }

    @Transactional
    fun unlinkClient(id: Long, userId: Long, actorEmail: String? = null) {
        val key = findKey(id)
        val user = userRepository.findById(userId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "User $userId not found", "NOT_FOUND")
        }
        if (user.accessKeys.none { it.id == id }) {
            throw ApiException(HttpStatus.NOT_FOUND, "Client is not linked to this account", "NOT_FOUND")
        }

        user.accessKeys.removeIf { it.id == id }
        userRepository.save(user)
        accountAccessService.evictUser(userId)
        accountAccessService.evictResource(key.resource)
        auditService.record(
            "KEY_UNLINKED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email,
            resource = key.resource
        )
    }

    @Transactional
    fun deleteKey(id: Long, actorEmail: String? = null) {
        val key = findKey(id)
        val resource = key.resource
        accessKeyRepository.delete(key)
        accountAccessService.evictResource(resource)
        gatewayService.evictResource(resource)
        auditService.record(
            "KEY_DELETED",
            actorEmail = actorEmail,
            resource = resource
        )
    }

    fun listClients(): List<ClientResponse> =
        userRepository.findAllByRoleOrderById(Role.CLIENT).map { it.toClientResponse() }

    fun stats(): AdminStatsResponse = AdminStatsResponse(
        users = AdminStatsResponse.UserStats(
            total = userRepository.count(),
            clients = userRepository.countByRole(Role.CLIENT),
            admins = userRepository.countByRole(Role.ADMIN),
            verified = userRepository.countByIsVerifiedTrue(),
            disabled = userRepository.countByDisabledAtIsNotNull()
        ),
        accessKeys = AdminStatsResponse.AccessKeyStats(
            total = accessKeyRepository.count(),
            active = accessKeyRepository.countByIsActiveTrue(),
            inactive = accessKeyRepository.count() - accessKeyRepository.countByIsActiveTrue()
        ),
        links = accessKeyRepository.countLinks()
    )

    private fun findKey(id: Long): AccessKey = accessKeyRepository.findById(id).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "Access key $id not found", "NOT_FOUND")
    }

    private fun validateEmoncmsKey(emoncmsKey: String) {
        if (!EmoncmsKey.isValid(emoncmsKey)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "The Emoncms key must be 16 to 64 letters or digits",
                "INVALID_KEY"
            )
        }
        if (!validateOnSave) return
        try {
            emoncmsClient.get("/feed/list.json", emptyMap(), emoncmsKey)
        } catch (ex: ApiException) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "The Emoncms key could not be validated",
                "INVALID_KEY"
            )
        }
    }

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
