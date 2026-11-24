package ma.electricwave.service

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.domain.AccessKey
import ma.electricwave.domain.SitePreference
import ma.electricwave.domain.User
import ma.electricwave.dto.ChangePasswordRequest
import ma.electricwave.dto.ClaimKeyRequest
import ma.electricwave.dto.MeAccessKeyResponse
import ma.electricwave.dto.MeResponse
import ma.electricwave.dto.SitePreferenceRequest
import ma.electricwave.dto.UpdateProfileRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.SitePreferenceRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.security.TokenRevocationService
import ma.electricwave.util.EmoncmsKey
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class MeService(
    private val userRepository: UserRepository,
    private val accessKeyRepository: AccessKeyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenService: RefreshTokenService,
    private val tokenRevocationService: TokenRevocationService,
    private val rateLimitService: RateLimitService,
    private val rateLimitProperties: RateLimitProperties,
    private val auditService: AuditService,
    private val cryptoService: CryptoService,
    private val sitePreferenceRepository: SitePreferenceRepository
) {

    fun get(userId: Long): MeResponse = requireUser(userId).toMeResponse()

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): MeResponse {
        val user = requireUser(userId)
        user.fullName = request.fullName?.trim()?.ifBlank { null }
        return userRepository.save(user).toMeResponse()
    }

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest, clientIp: String) {
        rateLimitService.check(
            "password:change:user",
            userId.toString(),
            rateLimitProperties.changePasswordPerUserPer10Minutes,
            Duration.ofMinutes(10)
        )

        val user = requireUser(userId)
        val current = user.password
        if (current == null || !passwordEncoder.matches(request.currentPassword, current)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Current password is incorrect",
                "VALIDATION_FAILED"
            )
        }
        if (passwordEncoder.matches(request.newPassword, current)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "New password must be different from the current password",
                "VALIDATION_FAILED"
            )
        }

        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
        refreshTokenService.revokeAllForUser(userId)
        auditService.record(
            "PASSWORD_CHANGE",
            actorUserId = user.id,
            actorEmail = user.email,
            ipAddress = clientIp
        )
    }

    fun logoutAll(userId: Long, clientIp: String) {
        val user = requireUser(userId)
        refreshTokenService.revokeAllForUser(userId)
        // "Everywhere" includes the caller's own access token; leaving it alive would let
        // this device keep reading for up to app.jwt.expiration after logging out.
        tokenRevocationService.revokeAllForUser(userId)
        auditService.record(
            "LOGOUT_ALL",
            actorUserId = user.id,
            actorEmail = user.email,
            ipAddress = clientIp
        )
    }

    /**
     * The accounts this client has claimed, each with its Emoncms key.
     *
     * The key is handed back because the mobile client reads Emoncms directly - this
     * service authenticates and stores keys, it is not in the data path. Returning it is
     * also what lets a reinstalled app recover: the link lives in the database, so signing
     * in again restores both the account and the key needed to read it.
     *
     * The trade-off is deliberate: once a key reaches a device it cannot be withdrawn by
     * disabling the account, only by rotating the key itself.
     */
    @Transactional(readOnly = true)
    fun listKeys(userId: Long): List<MeAccessKeyResponse> {
        val linkedAt = accessKeyRepository.findLinkedByUserId(userId)
            .associate { it.getAccessKeyId() to it.getLinkedAt() }
        // The whole set in one query, joined in memory. A client with a dozen sites
        // would otherwise cost a dozen lookups to render a list it already holds.
        val preferences = sitePreferenceRepository.findAllByUserId(userId)
            .associateBy { it.accessKeyId }
        return requireUser(userId).accessKeys
            .sortedBy { it.resource }
            .map { key ->
                toKeyResponse(key, preferences[key.id], linkedAt[key.id] ?: Instant.now())
            }
    }

    /**
     * Sets this user's own name, location or photo for one site.
     *
     * **The link is the authorisation.** Without that check any signed-in user could
     * relabel a site they were never given - and, once photos land, hang an image off
     * it. An unlinked id is a 404 rather than a 403 on purpose: whether a site exists
     * is not something a stranger should be able to probe.
     *
     * A null field is left alone, so a client changing only the name need not send the
     * address back. A blank string clears that field and returns it to the fallback.
     * Coordinates have no blank, so a pin can be moved but not removed; dropping one
     * would need a flag of its own, and nothing asks for that yet.
     */
    @Transactional
    fun updateSitePreference(
        userId: Long,
        accessKeyId: Long,
        request: SitePreferenceRequest
    ): MeAccessKeyResponse {
        val user = requireUser(userId)
        // An admin is deliberately **not** linked to the sites they manage, yet theirs is
        // the account that sees the whole fleet. A link alone as the way in made this
        // unusable from the one account that most needs it, so an admin may address any
        // registered site. The row stays personal either way: it is keyed to this user,
        // not to the site, so nothing an admin writes reaches a client's view.
        val key = user.accessKeys.firstOrNull { it.id == accessKeyId }
            ?: accessKeyRepository.findById(accessKeyId)
                .filter { user.isAdmin() }
                .orElseThrow { ApiException(HttpStatus.NOT_FOUND, "No such linked account") }

        val preference = sitePreferenceRepository
            .findByUserIdAndAccessKeyId(userId, accessKeyId)
            .orElseGet { SitePreference(userId = userId, accessKeyId = accessKeyId) }

        request.name?.let { preference.name = it.trim().ifBlank { null } }
        request.region?.let { preference.region = it.trim().ifBlank { null } }
        request.addressDetail?.let { preference.addressDetail = it.trim().ifBlank { null } }
        request.latitude?.let { preference.latitude = it }
        request.longitude?.let { preference.longitude = it }
        request.imageUrl?.let { preference.imageUrl = it.trim().ifBlank { null } }
        preference.updatedAt = Instant.now()

        val saved = sitePreferenceRepository.save(preference)
        val linkedAt = accessKeyRepository.findLinkedByUserId(userId)
            .firstOrNull { it.getAccessKeyId() == accessKeyId }
            ?.getLinkedAt()
        return toKeyResponse(key, saved, linkedAt ?: Instant.now())
    }

    /** One place that maps a key plus its optional preference, so the two paths agree. */
    private fun toKeyResponse(
        key: AccessKey,
        preference: SitePreference?,
        linkedAt: Instant
    ) = MeAccessKeyResponse(
        id = key.id,
        resource = key.resource,
        description = key.description,
        keyHint = key.keyHint,
        isActive = key.isActive,
        emoncmsKey = decryptOrNull(key),
        linkedAt = linkedAt,
        name = preference?.name,
        region = preference?.region,
        addressDetail = preference?.addressDetail,
        latitude = preference?.latitude,
        longitude = preference?.longitude,
        imageUrl = preference?.imageUrl
    )

    /**
     * A key encrypted under a master key this instance no longer holds cannot be read.
     * That is a configuration problem for one account, not a reason to fail the whole
     * list, so the entry comes back without its key and the client treats it as unusable.
     */
    private fun decryptOrNull(key: AccessKey): String? = try {
        cryptoService.decrypt(key.emoncmsApiKeyEnc)
    } catch (ex: Exception) {
        null
    }

    @Transactional
    fun claimKey(userId: Long, request: ClaimKeyRequest, clientIp: String): MeAccessKeyResponse {
        rateLimitService.check(
            "claim:user",
            userId.toString(),
            rateLimitProperties.claimPerUserPerMinute,
            Duration.ofMinutes(1)
        )
        rateLimitService.check(
            "claim:ip",
            clientIp,
            rateLimitProperties.claimPerIpPerMinute,
            Duration.ofMinutes(1)
        )

        val user = requireUser(userId)
        val normalized = EmoncmsKey.normalize(request.key)
        if (!EmoncmsKey.isValid(normalized)) {
            auditService.record(
                "KEY_LINK_FAILED",
                actorUserId = user.id,
                actorEmail = user.email,
                ipAddress = clientIp,
                details = mapOf("reason" to "malformed")
            )
            throw invalidKey()
        }

        val accessKey = accessKeyRepository.findByKeyHash(EmoncmsKey.hash(normalized)).orElse(null)
        if (accessKey == null || !accessKey.isActive) {
            auditService.record(
                "KEY_LINK_FAILED",
                actorUserId = user.id,
                actorEmail = user.email,
                ipAddress = clientIp,
                details = mapOf("reason" to "unknown_or_inactive")
            )
            throw invalidKey()
        }

        if (user.accessKeys.any { it.id == accessKey.id }) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "This Emoncms account is already linked to your profile",
                "CONFLICT"
            )
        }

        user.accessKeys.add(accessKey)
        userRepository.save(user)
        auditService.record(
            "KEY_LINKED",
            actorUserId = user.id,
            actorEmail = user.email,
            resource = accessKey.resource,
            ipAddress = clientIp
        )
        return MeAccessKeyResponse(
            id = accessKey.id,
            resource = accessKey.resource,
            description = accessKey.description,
            keyHint = accessKey.keyHint,
            isActive = accessKey.isActive,
            // Returned on the claim itself so the client can read immediately, without a
            // second round trip to fetch what it just linked.
            emoncmsKey = decryptOrNull(accessKey),
            linkedAt = Instant.now()
        )
    }

    @Transactional
    fun unlinkKey(userId: Long, accessKeyId: Long, clientIp: String) {
        val user = requireUser(userId)
        val accessKey = user.accessKeys.find { it.id == accessKeyId } ?: throw ApiException(
            HttpStatus.NOT_FOUND,
            "Linked Emoncms account not found",
            "NOT_FOUND"
        )

        user.accessKeys.remove(accessKey)
        userRepository.save(user)
        auditService.record(
            "KEY_UNLINKED",
            actorUserId = user.id,
            actorEmail = user.email,
            resource = accessKey.resource,
            ipAddress = clientIp
        )
    }

    private fun requireUser(userId: Long): User {
        val user = userRepository.findById(userId).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "Account no longer exists", "UNAUTHORIZED")
        }
        // Normally an access token for a disabled account has already been revoked, so
        // the decoder rejects it before reaching here. This is the backstop for the case
        // where the revocation marker is gone (Redis flushed or restarted) and keeps the
        // 403 ACCOUNT_DISABLED contract true for every authenticated call, not just login.
        if (user.disabledAt != null) {
            throw ApiException(HttpStatus.FORBIDDEN, "Account is disabled", "ACCOUNT_DISABLED")
        }
        return user
    }

    private fun invalidKey() = ApiException(
        HttpStatus.BAD_REQUEST,
        "Invalid Emoncms key",
        "INVALID_KEY"
    )

    private fun User.toMeResponse(): MeResponse = MeResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt
    )

    companion object {
        fun userIdFromClaim(uid: Any?): Long = when (uid) {
            is Number -> uid.toLong()
            is String -> uid.toLongOrNull()
            else -> null
        } ?: throw ApiException(
            HttpStatus.UNAUTHORIZED,
            "Invalid access token",
            "UNAUTHORIZED"
        )
    }
}
