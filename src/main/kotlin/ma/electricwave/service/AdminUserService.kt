package ma.electricwave.service

import ma.electricwave.domain.AuthProvider
import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.dto.AdminUserCreateRequest
import ma.electricwave.dto.AdminUserResponse
import ma.electricwave.dto.PageResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.security.TokenRevocationService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val accessKeyRepository: AccessKeyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenService: RefreshTokenService,
    private val tokenRevocationService: TokenRevocationService,
    private val accountAccessService: AccountAccessService,
    private val auditService: AuditService
) {

    @Transactional
    fun create(request: AdminUserCreateRequest, actorEmail: String? = null): AdminUserResponse {
        val email = request.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "An account with this email already exists", "CONFLICT")
        }
        val user = userRepository.save(
            User(
                email = email,
                password = passwordEncoder.encode(request.password),
                fullName = request.fullName?.trim()?.ifBlank { null },
                role = request.role ?: Role.CLIENT,
                authProvider = AuthProvider.LOCAL,
                isVerified = true
            )
        )
        auditService.record(
            "USER_CREATED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email,
            details = mapOf("role" to user.role.name)
        )
        return user.toResponse()
    }

    @Transactional(readOnly = true)
    fun search(query: String?, page: Int, size: Int): PageResponse<AdminUserResponse> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, PageResponse.MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.ASC, "id")
        )
        val normalized = query?.trim()?.ifBlank { null }
        val result = if (normalized == null) {
            userRepository.findAll(pageable)
        } else {
            userRepository.searchByQuery(escapeLikePattern(normalized), pageable)
        }
        // Resources for the whole page in one query; mapping user.accessKeys per row
        // lazy-loads once per user, so a full page cost 101 queries.
        val resourcesByUser = resourcesFor(result.content.map { it.id })
        return PageResponse.from(result.map { user -> user.toResponse(resourcesByUser[user.id].orEmpty()) })
    }

    @Transactional
    fun updateRole(id: Long, role: Role, actorEmail: String): AdminUserResponse {
        val user = findUser(id)
        if (user.email.equals(actorEmail, ignoreCase = true)) {
            throw ApiException(HttpStatus.CONFLICT, "You cannot change your own role", "CONFLICT")
        }
        if (user.role == Role.ADMIN && role != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "Cannot demote the last remaining admin",
                "CONFLICT"
            )
        }

        val previousRole = user.role
        if (role == Role.ADMIN && previousRole != Role.ADMIN) {
            user.accessKeys.clear()
            accountAccessService.evictUser(user.id)
        }
        user.role = role
        val saved = userRepository.save(user)
        // The old access token still carries the old `roles` claim, so revoking the
        // refresh family alone would leave a demoted admin with admin authority until
        // that token expired.
        revokeEverySession(user.id)
        auditService.record(
            "ROLE_CHANGED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email,
            details = mapOf("from" to previousRole.name, "to" to role.name)
        )
        return saved.toResponse()
    }

    @Transactional
    fun disable(id: Long, actorEmail: String): AdminUserResponse {
        val user = findUser(id)
        if (user.email.equals(actorEmail, ignoreCase = true)) {
            throw ApiException(HttpStatus.CONFLICT, "You cannot disable your own account", "CONFLICT")
        }
        if (user.role == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "Cannot disable the last remaining admin",
                "CONFLICT"
            )
        }

        user.disabledAt = Instant.now()
        val saved = userRepository.save(user)
        revokeEverySession(user.id)
        accountAccessService.evictUser(user.id)
        auditService.record(
            "USER_DISABLED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email
        )
        return saved.toResponse()
    }

    @Transactional
    fun enable(id: Long, actorEmail: String): AdminUserResponse {
        val user = findUser(id)
        user.disabledAt = null
        val saved = userRepository.save(user)
        accountAccessService.evictUser(user.id)
        auditService.record(
            "USER_ENABLED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email
        )
        return saved.toResponse()
    }

    fun revokeSessions(id: Long, actorEmail: String) {
        val user = findUser(id)
        revokeEverySession(user.id)
        auditService.record(
            "SESSIONS_REVOKED",
            actorEmail = actorEmail,
            targetUserId = user.id,
            targetEmail = user.email
        )
    }

    /**
     * Escapes the characters LIKE treats as wildcards so a search term is matched
     * literally. Pairs with the `escape '!'` clause on the query; the escape character
     * itself is doubled first so it cannot be used to neutralise the others.
     */
    internal fun escapeLikePattern(query: String): String = query
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")

    /** Revokes both halves of a session: the refresh family and any live access token. */
    private fun revokeEverySession(userId: Long) {
        refreshTokenService.revokeAllForUser(userId)
        tokenRevocationService.revokeAllForUser(userId)
    }

    private fun findUser(id: Long): User = userRepository.findById(id).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "User $id not found", "NOT_FOUND")
    }

    private fun resourcesFor(userIds: List<Long>): Map<Long, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return accessKeyRepository.findResourcesByUserIds(userIds)
            .groupBy({ it.getUserId() }, { it.getResource() })
    }

    private fun User.toResponse(): AdminUserResponse = toResponse(accessKeys.map { it.resource })

    private fun User.toResponse(resources: List<String>): AdminUserResponse = AdminUserResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt,
        disabled = disabledAt != null,
        accessKeys = resources
    )
}
