package ma.electricwave.service

import ma.electricwave.config.RateLimitProperties
import ma.electricwave.domain.AccessKey
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
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import ma.electricwave.security.GoogleTokenVerifier
import ma.electricwave.security.JwtProperties
import ma.electricwave.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val accessKeyRepository: AccessKeyRepository,
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
            throw ApiException(HttpStatus.CONFLICT, "An account with this email already exists")
        }

        val user = User(
            email = email,
            password = passwordEncoder.encode(request.password),
            fullName = request.fullName?.trim()?.ifBlank { null },
            role = Role.CLIENT,
            authProvider = AuthProvider.LOCAL,
            isVerified = false
        )
        user.accessKeys += resolveKeys(request.accessKeys)
        userRepository.save(user)

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
        val user = userRepository.findByEmail(email).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "No account found for this email")
        }
        if (user.isVerified) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Account is already verified")
        }
        if (!verificationCodeService.verify(email, request.code)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code")
        }

        user.isVerified = true
        userRepository.save(user)
        verificationCodeService.clear(email)
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

    @Transactional(readOnly = true)
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

        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(email, request.password))

        val user = userRepository.findByEmail(email).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        if (!user.isVerified) {
            throw ApiException(HttpStatus.FORBIDDEN, "Account not verified. Please verify your email first.")
        }

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

        val userId = refreshTokenService.consume(request.refreshToken)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")
        val user = userRepository.findById(userId).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")
        }
        if (!user.isVerified) {
            throw ApiException(HttpStatus.FORBIDDEN, "Account not verified. Please verify your email first.")
        }

        return issueTokens(user)
    }

    fun logout(request: RefreshTokenRequest) {
        refreshTokenService.revoke(request.refreshToken)
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
        val keys = resolveKeys(request.accessKeys)

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
        if (existing != null && !existing.isVerified) {
            existing.isVerified = true
        }
        if (keys.isNotEmpty()) {
            user.accessKeys.addAll(keys)
        }

        return issueTokens(userRepository.save(user))
    }

    private fun issueTokens(user: User): TokenResponse = TokenResponse(
        token = jwtService.issue(user),
        email = user.email,
        role = user.role.name,
        refreshToken = refreshTokenService.issue(user.id),
        expiresIn = jwtProperties.expiration.seconds
    )

    private fun resolveKeys(rawKeys: List<String>): List<AccessKey> = rawKeys.map { raw ->
        val keyValue = raw.trim()
        accessKeyRepository.findByKeyValue(keyValue)
            .filter { it.isActive }
            .orElseThrow {
                ApiException(HttpStatus.BAD_REQUEST, "Access key '$keyValue' does not exist or is not active")
            }
    }.distinctBy { it.id }

    private fun devCode(code: String): String? =
        if (exposeVerificationCode && !mailService.enabled) code else null
}
