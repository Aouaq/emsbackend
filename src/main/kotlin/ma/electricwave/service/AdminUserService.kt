package ma.electricwave.service

import ma.electricwave.domain.AuthProvider
import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.dto.AdminUserCreateRequest
import ma.electricwave.dto.AdminUserResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun create(request: AdminUserCreateRequest): AdminUserResponse {
        val email = request.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "An account with this email already exists")
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
        return user.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(): List<AdminUserResponse> =
        userRepository.findAll().sortedBy { it.id }.map { it.toResponse() }

    @Transactional
    fun updateRole(id: Long, role: Role, actorEmail: String): AdminUserResponse {
        val user = userRepository.findById(id).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "User $id not found")
        }
        if (user.email.equals(actorEmail, ignoreCase = true)) {
            throw ApiException(HttpStatus.CONFLICT, "You cannot change your own role")
        }
        if (user.role == Role.ADMIN && role != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw ApiException(HttpStatus.CONFLICT, "Cannot demote the last remaining admin")
        }
        user.role = role
        return userRepository.save(user).toResponse()
    }

    private fun User.toResponse(): AdminUserResponse = AdminUserResponse(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        provider = authProvider.name,
        verified = isVerified,
        createdAt = createdAt,
        accessKeys = accessKeys.map { it.keyValue }
    )
}
