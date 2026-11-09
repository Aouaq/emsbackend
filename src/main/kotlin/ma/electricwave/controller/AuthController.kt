package ma.electricwave.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import ma.electricwave.dto.ForgotPasswordRequest
import ma.electricwave.dto.ForgotPasswordResponse
import ma.electricwave.dto.GoogleLoginRequest
import ma.electricwave.dto.LoginRequest
import ma.electricwave.dto.RefreshTokenRequest
import ma.electricwave.dto.RegisterRequest
import ma.electricwave.dto.RegisterResponse
import ma.electricwave.dto.ResendVerificationRequest
import ma.electricwave.dto.ResendVerificationResponse
import ma.electricwave.dto.ResetPasswordRequest
import ma.electricwave.dto.TokenResponse
import ma.electricwave.dto.VerifyRequest
import ma.electricwave.dto.VerifyResponse
import ma.electricwave.security.ClientIpResolver
import ma.electricwave.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Authentication", description = "Registration, email verification, login, token refresh and Google sign-in")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val clientIpResolver: ClientIpResolver
) {

    @Operation(
        summary = "Register a client account",
        description = "Creates an unverified CLIENT account with no linked Emoncms accounts. " +
            "A 6-digit verification code is stored in Redis for 10 minutes and sent by email."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account created; verification code sent"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "409", description = "An account with this email already exists"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        http: HttpServletRequest
    ): ResponseEntity<RegisterResponse> =
        ResponseEntity.status(201).body(authService.register(request, clientIpResolver.resolve(http)))

    @Operation(
        summary = "Verify the email address",
        description = "Checks the code stored under verify:<email> in Redis, marks the user as verified " +
            "in PostgreSQL and clears the Redis entry."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Email verified"
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid/expired code, already verified, or unknown email " +
                "(unknown email is intentionally folded into this same code to avoid account enumeration)"
        ),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody request: VerifyRequest,
        http: HttpServletRequest
    ): VerifyResponse = authService.verify(request, clientIpResolver.resolve(http))

    @Operation(
        summary = "Resend the verification code",
        description = "Issues a new 6-digit code (invalidating the previous one). The response is intentionally " +
            "neutral for unknown or already-verified emails to avoid account enumeration."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Request accepted"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/verify/resend")
    fun resendVerification(
        @Valid @RequestBody request: ResendVerificationRequest,
        http: HttpServletRequest
    ): ResendVerificationResponse = authService.resendVerification(request, clientIpResolver.resolve(http))

    @Operation(
        summary = "Log in and issue tokens",
        description = "Authenticates email/password, requires a verified account and returns a Bearer JWT " +
            "plus a rotating refresh token."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Tokens issued"),
        ApiResponse(responseCode = "401", description = "Invalid email or password"),
        ApiResponse(responseCode = "403", description = "Account is not verified"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        http: HttpServletRequest
    ): TokenResponse = authService.login(request, clientIpResolver.resolve(http))

    @Operation(
        summary = "Rotate the refresh token",
        description = "Consumes the supplied refresh token and returns a new access/refresh pair. " +
            "A refresh token can be used only once."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "New tokens issued"),
        ApiResponse(responseCode = "401", description = "Invalid, expired or already-used refresh token"),
        ApiResponse(responseCode = "403", description = "Account is disabled"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
        http: HttpServletRequest
    ): TokenResponse = authService.refresh(request, clientIpResolver.resolve(http))

    @Operation(
        summary = "Log out",
        description = "Revokes the supplied refresh token. Access tokens remain valid until they expire."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Refresh token revoked"),
        ApiResponse(responseCode = "400", description = "Validation failed")
    )
    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: RefreshTokenRequest,
        http: HttpServletRequest
    ): ResponseEntity<Void> {
        authService.logout(request, clientIpResolver.resolve(http))
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Request a password reset code",
        description = "Sends a 6-digit reset code when the account exists, is verified and is active. " +
            "The response is intentionally neutral to avoid account enumeration."
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Request accepted"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/password/forgot")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        http: HttpServletRequest
    ): ResponseEntity<ForgotPasswordResponse> =
        ResponseEntity.status(202)
            .body(authService.forgotPassword(request, clientIpResolver.resolve(http)))

    @Operation(
        summary = "Reset a forgotten password",
        description = "Verifies the emailed reset code, replaces the password, revokes every refresh token " +
            "for the account and records a PASSWORD_RESET audit event."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Password reset"),
        ApiResponse(responseCode = "400", description = "Invalid or expired reset code"),
        ApiResponse(responseCode = "403", description = "Account is disabled"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    )
    @PostMapping("/password/reset")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
        http: HttpServletRequest
    ): ResponseEntity<Void> {
        authService.resetPassword(request, clientIpResolver.resolve(http))
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Sign in with Google (native apps)",
        description = "Verifies a Google ID token locally, creates or upgrades the account and returns " +
            "an access token plus a refresh token. Requires GOOGLE_CLIENT_ID to be configured."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Tokens issued"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Invalid Google ID token"),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        ApiResponse(responseCode = "503", description = "Google sign-in is not configured")
    )
    @PostMapping("/oauth2/google")
    fun googleLogin(
        @Valid @RequestBody request: GoogleLoginRequest,
        http: HttpServletRequest
    ): TokenResponse = authService.loginWithGoogle(request, clientIpResolver.resolve(http))
}
