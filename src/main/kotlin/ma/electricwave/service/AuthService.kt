package ma.electricwave.service

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.domain.AuthProvider
import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.dto.GoogleLoginRequest
import ma.electricwave.dto.LoginRequest
import ma.electricwave.dto.RefreshTokenRequest
import ma.electricwave.dto.RegisterRequest
import ma.electricwave.dto.RegisterResponse
import ma.electricwave.dto.ResendVerificationRequest
import ma.electricwave.dto.ResendVerificationResponse
import ma.electricwave.dto.TokenResponse
import ma.electricwave.dto.VerifyRequest
import ma.electricwave.dto.VerifyResponse
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.UserRepository
import ma.electricwave.security.GoogleTokenVerifier
import ma.electricwave.security.JwtProperties
import ma.electricwave.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val verificationCodeService: VerificationCodeService,
    private val mailService: MailService,
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val refreshTokenService: RefreshTokenService,
    private val rateLimitService: RateLimitService,
    private val rateLimitProperties: RateLimitProperties,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val auditService: AuditService,
    @Value("\${app.auth.expose-verification-code}") private val exposeVerificationCode: Boolean = false
) {

    @Transactional
    fun register(request: RegisterRequest, clientIp: String): RegisterResponse {
        rateLimitService.check(
            "register:ip",
            clientIp,
            rateLimitProperties.registerIpPerHour,
            Duration.ofHours(1)
        )

        val email = request.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) {
            throw ApiException(HttpStatus.CONFLICT, "An account with this email already exists", "CONFLICT")
        }

        val user = User(
            email = email,
            password = passwordEncoder.encode(request.password),
            fullName = request.fullName?.trim()?.ifBlank { null },
            role = Role.CLIENT,
            authProvider = AuthProvider.LOCAL,
            isVerified = false
        )
        userRepository.save(user)
        auditService.record(
            "REGISTER",
            actorUserId = user.id,
            actorEmail = email,
            ipAddress = clientIp
        )

        val code = verificationCodeService.issue(email)
        mailService.sendVerificationCode(email, code)

        return RegisterResponse(
            email = email,
            verified = false,
            message = "Registration successful. A 6-digit verification code has been sent to your email and expires in 10 minutes.",
            devVerificationCode = devCode(code)
        )
    }

    @Transactional
    fun verify(request: VerifyRequest, clientIp: String): VerifyResponse {
        rateLimitService.check(
            "verify:ip",
            clientIp,
            rateLimitProperties.verifyIpPer10Minutes,
            Duration.ofMinutes(10)
        )

        val email = request.email.trim().lowercase()
        val user = userRepository.findByEmail(email).orElse(null)
        val codeValid = verificationCodeService.verify(email, request.code)
        if (user == null || user.isVerified || !codeValid) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Invalid or expired verification code",
                "INVALID_CODE"
            )
        }

        user.isVerified = true
        userRepository.save(user)
        verificationCodeService.clear(email)
        auditService.record(
            "VERIFY_EMAIL",
            actorUserId = user.id,
            actorEmail = email,
            ipAddress = clientIp
        )
        return VerifyResponse(email = email, verified = true, message = "Email verified successfully")
    }

    @Transactional
    fun resendVerification(request: ResendVerificationRequest, clientIp: String): ResendVerificationResponse {
        val email = request.email.trim().lowercase()
        rateLimitService.check(
            "resend:email",
            email,
            rateLimitProperties.resendEmailPer10Minutes,
            Duration.ofMinutes(10)
        )
        rateLimitService.check(
            "resend:ip",
            clientIp,
            rateLimitProperties.resendIpPerHour,
            Duration.ofHours(1)
        )

        val user = userRepository.findByEmail(email).orElse(null)
        var devCode: String? = null
        if (user != null && !user.isVerified) {
            val code = verificationCodeService.issue(email)
            mailService.sendVerificationCode(email, code)
            devCode = devCode(code)
        }

        return ResendVerificationResponse(
            message = "If the account exists and is not verified, a new verification code has been sent.",
            devVerificationCode = devCode
        )
    }

    @Transactional
    fun login(request: LoginRequest, clientIp: String): TokenResponse {
        val email = request.email.trim().lowercase()
        rateLimitService.check(
            "login:ip",
            clientIp,
            rateLimitProperties.loginIpPerMinute,
            Duration.ofMinutes(1)
        )
        rateLimitService.check(
            "login:email",
            email,
            rateLimitProperties.loginEmailPerMinute,
            Duration.ofMinutes(1)
        )

        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken(email, request.password))
        } catch (ex: AuthenticationException) {
            auditService.record(
                "LOGIN_FAILURE",
                actorEmail = email,
                ipAddress = clientIp,
                details = mapOf("reason" to "bad_credentials")
            )
            throw ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                "INVALID_CREDENTIALS"
            )
        }

        val user = userRepository.findByEmail(email).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password", "INVALID_CREDENTIALS")
        }
        assertAccountUsable(user, clientIp, auditFailures = true)

        user.lastLoginAt = Instant.now()
        userRepository.save(user)
        auditService.record(
            "LOGIN_SUCCESS",
            actorUserId = user.id,
            actorEmail = email,
            ipAddress = clientIp
        )

        return issueTokens(user)
    }

    @Transactional(readOnly = true)
    fun refresh(request: RefreshTokenRequest, clientIp: String): TokenResponse {
        rateLimitService.check(
            "refresh:ip",
            clientIp,
            rateLimitProperties.refreshIpPerMinute,
            Duration.ofMinutes(1)
        )

        return when (val result = refreshTokenService.consume(request.refreshToken)) {
            is RefreshConsumeResult.Active -> {
                val user = userRepository.findById(result.userId).orElseThrow {
                    ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid or expired refresh token",
                        "INVALID_REFRESH_TOKEN"
                    )
                }
                assertAccountUsable(user, clientIp, auditFailures = false)
                issueTokens(user, result.familyId)
            }

            is RefreshConsumeResult.Reused -> {
                auditService.record(
                    "REFRESH_REUSE_DETECTED",
                    actorUserId = result.userId,
                    ipAddress = clientIp,
                    details = mapOf("familyId" to result.familyId)
                )
                throw ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token reuse detected; sessions revoked",
                    "REFRESH_TOKEN_REUSED"
                )
            }

            RefreshConsumeResult.Invalid -> throw ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired refresh token",
                "INVALID_REFRESH_TOKEN"
            )
        }
    }

    fun logout(request: RefreshTokenRequest, clientIp: String) {
        val revoked = refreshTokenService.revoke(request.refreshToken)
        if (revoked != null) {
            auditService.record(
                "LOGOUT",
                actorUserId = revoked.userId,
                ipAddress = clientIp,
                details = mapOf("familyId" to revoked.familyId)
            )
        }
    }

    @Transactional
    fun loginWithGoogle(request: GoogleLoginRequest, clientIp: String): TokenResponse {
        rateLimitService.check(
            "google:ip",
            clientIp,
            rateLimitProperties.googleIpPer10Minutes,
            Duration.ofMinutes(10)
        )

        val googleUser = googleTokenVerifier.verify(request.idToken)
        val email = googleUser.email.trim().lowercase()

        val existing = userRepository.findByEmail(email).orElse(null)
        val user = existing ?: userRepository.save(
            User(
                email = email,
                fullName = googleUser.name?.trim()?.ifBlank { null },
                role = Role.CLIENT,
                authProvider = AuthProvider.GOOGLE,
                isVerified = true
            )
        )

        if (user.disabledAt != null) {
            auditService.record(
                "LOGIN_FAILURE",
                actorUserId = user.id,
                actorEmail = email,
                ipAddress = clientIp,
                details = mapOf("reason" to "disabled")
            )
            throw ApiException(HttpStatus.FORBIDDEN, "Account is disabled", "ACCOUNT_DISABLED")
        }

        if (!user.isVerified) {
            user.isVerified = true
            if (user.authProvider == AuthProvider.LOCAL) {
                user.password = null
                user.authProvider = AuthProvider.GOOGLE
            }
        }
        user.lastLoginAt = Instant.now()
        userRepository.save(user)

        auditService.record(
            "GOOGLE_LOGIN",
            actorUserId = user.id,
            actorEmail = email,
            ipAddress = clientIp
        )
        return issueTokens(user)
    }

    private fun assertAccountUsable(user: User, clientIp: String, auditFailures: Boolean) {
        if (user.disabledAt != null) {
            if (auditFailures) {
                auditService.record(
                    "LOGIN_FAILURE",
                    actorUserId = user.id,
                    actorEmail = user.email,
                    ipAddress = clientIp,
                    details = mapOf("reason" to "disabled")
                )
            }
            throw ApiException(HttpStatus.FORBIDDEN, "Account is disabled", "ACCOUNT_DISABLED")
        }
        if (!user.isVerified) {
            if (auditFailures) {
                auditService.record(
                    "LOGIN_FAILURE",
                    actorUserId = user.id,
                    actorEmail = user.email,
                    ipAddress = clientIp,
                    details = mapOf("reason" to "unverified")
                )
            }
            throw ApiException(
                HttpStatus.FORBIDDEN,
                "Account not verified. Please verify your email first.",
                "ACCOUNT_NOT_VERIFIED"
            )
        }
    }

    private fun issueTokens(user: User, familyId: String? = null): TokenResponse = TokenResponse(
        token = jwtService.issue(user),
        email = user.email,
        role = user.role.name,
        refreshToken = refreshTokenService.issue(user.id, familyId),
        expiresIn = jwtProperties.expiration.seconds
    )

    private fun devCode(code: String): String? =
        if (exposeVerificationCode && !mailService.enabled) code else null
}
