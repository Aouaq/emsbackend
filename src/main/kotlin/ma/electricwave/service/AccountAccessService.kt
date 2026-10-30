package ma.electricwave.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import ma.electricwave.repository.AccessKeyRepository
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AccountAccessService(private val accessKeyRepository: AccessKeyRepository) {

    private data class AccessKey(val userId: Long, val resource: String)

    private val cache: Cache<AccessKey, Boolean> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .build()

    fun canAccess(userId: Long, resource: String): Boolean =
        cache.get(AccessKey(userId, resource)) {
            accessKeyRepository.findAccessible(userId, resource).isPresent
        }

    fun evictUser(userId: Long) {
        cache.asMap().keys.removeIf { it.userId == userId }
    }

    fun evictResource(resource: String) {
        cache.asMap().keys.removeIf { it.resource == resource }
    }
}
