package ma.electricwave.service

import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.AlarmKind
import ma.electricwave.domain.Role
import ma.electricwave.domain.SitePreference
import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.dto.AccessKeyResponse
import ma.electricwave.dto.AccessKeyUpdateRequest
import ma.electricwave.dto.AdminStatsResponse
import ma.electricwave.dto.ClientResponse
import ma.electricwave.dto.LinkedClientResponse
import ma.electricwave.dto.ReplaceEmoncmsKeyRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.AlarmRepository
import ma.electricwave.repository.SitePreferenceRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.util.EmoncmsKey
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class AdminService(
    private val accessKeyRepository: AccessKeyRepository,
    private val userRepository: UserRepository,
    private val alarmRepository: AlarmRepository,
    private val cryptoService: CryptoService,
    private val auditService: AuditService,
    private val sitePreferenceRepository: SitePreferenceRepository,
    private val entityManager: EntityManager
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
        auditService.record(
            "EMONCMS_KEY_REPLACED",
            actorEmail = actorEmail,
            resource = key.resource
        )
        return saved.toResponse()
    }

    /**
     * Toggles the active flag atomically. Returns the key as it now stands.
     *
     * The flag is flipped by the database rather than read, negated and written back, so
     * two concurrent toggles cannot both act on the same observed value.
     */
    @Transactional
    fun toggleKeyActive(id: Long, actorEmail: String? = null): AccessKeyResponse {
        val key = findKey(id)
        val resource = key.resource
        if (accessKeyRepository.toggleActive(id, Instant.now()) == 0) {
            throw ApiException(HttpStatus.NOT_FOUND, "Access key $id not found", "NOT_FOUND")
        }
        // The update bypassed the persistence context, so the loaded instance is stale.
        entityManager.refresh(key)

        auditService.record(
            "KEY_UPDATED",
            actorEmail = actorEmail,
            resource = resource,
            details = mapOf("isActive" to key.isActive)
        )
        return key.toResponse()
    }

    /**
     * Every registered account, carrying the **caller's own** site preferences.
     *
     * [callerUserId] is nullable so an internal caller that only wants the accounts need
     * not invent one: passing null leaves the preference fields empty rather than
     * showing somebody else's, which is the only safe default for a personal field on a
     * fleet-wide listing.
     */
    fun listKeys(callerUserId: Long? = null): List<AccessKeyResponse> {
        // One query for the caller's whole set, joined in memory - the same shape the
        // client listing uses, and for the same reason: a lookup per site would cost a
        // dozen round trips to render a list already in hand.
        val preferences = callerUserId
            ?.let { id ->
                sitePreferenceRepository.findAllByUserId(id).associateBy { it.accessKeyId }
            }
            ?: emptyMap()
        return accessKeyRepository.findAllWithUsers().map { it.toResponse(preferences[it.id]) }
    }

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
        auditService.record(
            "KEY_DELETED",
            actorEmail = actorEmail,
            resource = resource
        )
    }

    fun listClients(): List<ClientResponse> {
        val clients = userRepository.findAllByRoleOrderById(Role.CLIENT)
        // One query for every client's active resources rather than a lazy load per
        // client. Still unbounded in the number of clients: narrowing that would change
        // the shape of a documented response, so it is left as a deliberate follow-up.
        val resourcesByUser = if (clients.isEmpty()) {
            emptyMap()
        } else {
            accessKeyRepository.findResourcesByUserIds(clients.map { it.id })
                .filter { it.getIsActive() }
                .groupBy({ it.getUserId() }, { it.getResource() })
        }
        return clients.map { it.toClientResponse(resourcesByUser[it.id].orEmpty()) }
    }

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
        links = accessKeyRepository.countLinks(),
        alarms = AdminStatsResponse.AlarmStats(
            open = alarmRepository.countByClearedAtIsNull(),
            overPower = alarmRepository.countByClearedAtIsNullAndKind(AlarmKind.OVER_POWER),
            underVoltage = alarmRepository.countByClearedAtIsNullAndKind(AlarmKind.UNDER_VOLTAGE),
            total = alarmRepository.count()
        )
    )

    /** A key this instance cannot decrypt is reported as absent rather than failing the list. */
    private fun decryptOrNull(key: AccessKey): String? = try {
        cryptoService.decrypt(key.emoncmsApiKeyEnc)
    } catch (ex: Exception) {
        null
    }

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
    }

    private fun AccessKey.toResponse(
        preference: SitePreference? = null
    ): AccessKeyResponse = AccessKeyResponse(
        id = id,
        resource = resource,
        description = description,
        isActive = isActive,
        keyHint = keyHint,
        emoncmsKeyConfigured = emoncmsApiKeyEnc.isNotEmpty(),
        emoncmsKey = decryptOrNull(this),
        linkedClients = linkedUsers.count { it.role == Role.CLIENT },
        createdAt = createdAt,
        updatedAt = updatedAt,
        name = preference?.name,
        region = preference?.region,
        addressDetail = preference?.addressDetail,
        latitude = preference?.latitude,
        longitude = preference?.longitude,
        imageUrl = preference?.imageUrl
    )

    private fun ma.electricwave.domain.User.toClientResponse(
        activeResources: List<String>
    ): ClientResponse = ClientResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        accessKeys = activeResources
    )
}
