package ma.electricwave.service

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.domain.User
import ma.electricwave.dto.ChangePasswordRequest
import ma.electricwave.dto.ClaimKeyRequest
import ma.electricwave.dto.MeAccessKeyResponse
import ma.electricwave.dto.MeResponse
import ma.electricwave.dto.UpdateProfileRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
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
    private val rateLimitService: RateLimitService,
    private val rateLimitProperties: RateLimitProperties,
    private val auditService: AuditService,
    private val accountAccessService: AccountAccessService
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
        auditService.record(
            "LOGOUT_ALL",
            actorUserId = user.id,
            actorEmail = user.email,
            ipAddress = clientIp
        )
    }

    fun listKeys(userId: Long): List<MeAccessKeyResponse> =
        accessKeyRepository.findLinkedByUserId(userId).map { key ->
            MeAccessKeyResponse(
                id = key.getAccessKeyId(),
                resource = key.getResource(),
                description = key.getDescription(),
                keyHint = key.getKeyHint(),
                isActive = key.getIsActive(),
                linkedAt = key.getLinkedAt()
            )
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
        accountAccessService.evictUser(user.id)
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
        accountAccessService.evictUser(user.id)
        auditService.record(
            "KEY_UNLINKED",
            actorUserId = user.id,
            actorEmail = user.email,
            resource = accessKey.resource,
            ipAddress = clientIp
        )
    }

    private fun requireUser(userId: Long): User = userRepository.findById(userId).orElseThrow {
        ApiException(HttpStatus.UNAUTHORIZED, "Account no longer exists", "UNAUTHORIZED")
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
