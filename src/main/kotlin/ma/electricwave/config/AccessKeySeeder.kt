package ma.electricwave.config

import ma.electricwave.dto.AccessKeyRequest
import ma.electricwave.exception.ApiException
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.service.AdminService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Emoncms accounts to register on first boot, so a fresh database is usable.
 *
 * These are secrets: an Emoncms read key is all that is needed to pull an installation's
 * data. They therefore belong in `config/application.yml` or the environment, both of
 * which are gitignored - never in a committed profile. `config/application.yml.example`
 * shows the shape.
 */
@ConfigurationProperties(prefix = "app.seed")
data class AccessKeySeedProperties(
    val accessKeys: List<SeedAccessKey> = emptyList()
) {
    data class SeedAccessKey(
        /** Lowercase slug, matching the Emoncms account name. */
        val resource: String = "",
        val key: String = "",
        val description: String? = null
    )
}

/**
 * Registers the configured Emoncms accounts if they are not present already.
 *
 * This is a seeder rather than a Flyway migration because neither stored column can be
 * written as a literal: `emoncms_api_key_enc` is AES-GCM ciphertext under the configured
 * crypto master key, and `key_hash` is derived from the plaintext. A migration carrying
 * pre-encrypted bytes would only decrypt under the one key it was produced with, and
 * would silently yield unreadable accounts under any other.
 *
 * Delegating to [AdminService.createKey] keeps hashing, hinting, encryption, validation
 * and the audit record in exactly one place, so seeded accounts are indistinguishable
 * from ones added through the admin API.
 */
@Component
@EnableConfigurationProperties(AccessKeySeedProperties::class)
class AccessKeySeeder(
    private val adminService: AdminService,
    private val accessKeyRepository: AccessKeyRepository,
    private val properties: AccessKeySeedProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AccessKeySeeder::class.java)

    override fun run(args: ApplicationArguments) {
        if (properties.accessKeys.isEmpty()) return

        var created = 0
        var skipped = 0

        for (seed in properties.accessKeys) {
            val resource = seed.resource.trim()
            if (resource.isEmpty() || seed.key.isBlank()) {
                log.warn("Skipping an app.seed.access-keys entry with a blank resource or key")
                continue
            }

            // Checked rather than inferred from a CONFLICT, so that a genuine collision -
            // the same Emoncms key already registered under a *different* resource - is
            // still reported instead of being silently counted as "already seeded".
            if (accessKeyRepository.existsByResource(resource)) {
                skipped++
                continue
            }

            try {
                adminService.createKey(
                    AccessKeyRequest(
                        emoncmsKey = seed.key,
                        resource = resource,
                        description = seed.description ?: resource,
                        isActive = true
                    ),
                    actorEmail = "seed"
                )
                created++
            } catch (ex: ApiException) {
                // One bad entry must not stop the rest, and must not stop the application:
                // a seed list is convenience, not a precondition for serving.
                log.warn("Seeding resource '{}' failed: {}", resource, ex.message)
            }
        }

        if (created > 0 || skipped > 0) {
            log.info(
                "Access key seeding complete: {} created, {} already present",
                created,
                skipped
            )
        }
    }
}
