package ma.electricwave.config

import ma.electricwave.domain.Role
import ma.electricwave.domain.User
import ma.electricwave.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.admin.email}") private val adminEmail: String,
    @Value("\${app.admin.password}") private val adminPassword: String,
    @Value("\${app.admin.full-name:}") private val adminName: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        val email = adminEmail.trim().lowercase()
        if (email.isBlank() || adminPassword.isBlank()) {
            log.warn("Admin seeding skipped: app.admin.email and app.admin.password must both be set.")
            return
        }
        if (!userRepository.existsByRole(Role.ADMIN)) {
            if (userRepository.existsByEmail(email)) {
                log.warn(
                    "No ADMIN exists and app.admin.email={} maps to an existing account. Seed a different admin email.",
                    email
                )
                return
            }
            userRepository.save(
                User(
                    email = email,
                    password = passwordEncoder.encode(adminPassword),
                    fullName = adminName.ifBlank { "System Administrator" },
                    role = Role.ADMIN,
                    isVerified = true
                )
            )
            log.info("Initial admin account created for {}", email)
        }
    }
}
