package ma.electricwave.repository

import ma.electricwave.domain.AccessKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface LinkedAccessKeyProjection {
    fun getAccessKeyId(): Long
    fun getResource(): String
    fun getDescription(): String?
    fun getKeyHint(): String
    fun getIsActive(): Boolean
    fun getLinkedAt(): Instant
}

interface LinkedClientProjection {
    fun getUserId(): Long
    fun getEmail(): String
    fun getFullName(): String?
    fun getLinkedAt(): Instant
}

interface AccessKeyRepository : JpaRepository<AccessKey, Long> {

    fun findByKeyHash(keyHash: String): Optional<AccessKey>

    fun existsByKeyHash(keyHash: String): Boolean

    fun existsByResource(resource: String): Boolean

    fun findByResource(resource: String): Optional<AccessKey>

    fun findAllByIsActiveTrueOrderByResource(): List<AccessKey>

    fun countByIsActiveTrue(): Long

    @Query(
        """
        select k from AccessKey k
        join k.linkedUsers u
        where u.id = :userId
          and k.resource = :resource
          and k.isActive = true
          and u.disabledAt is null
        """
    )
    fun findAccessible(
        @Param("userId") userId: Long,
        @Param("resource") resource: String
    ): Optional<AccessKey>

    @Query(
        "select distinct k from AccessKey k left join fetch k.linkedUsers order by k.id"
    )
    fun findAllWithUsers(): List<AccessKey>

    @Query(
        "select distinct k from AccessKey k left join fetch k.linkedUsers where k.id = :id"
    )
    fun findWithUsersById(@Param("id") id: Long): Optional<AccessKey>

    @Query(
        value = """
            select u.id as userId,
                   u.email as email,
                   u.full_name as fullName,
                   uak.linked_at as linkedAt
            from users u
            join user_access_keys uak on uak.user_id = u.id
            where uak.access_key_id = :accessKeyId
            order by u.email
        """,
        nativeQuery = true
    )
    fun findLinkedClients(@Param("accessKeyId") accessKeyId: Long): List<LinkedClientProjection>

    @Query(value = "select count(*) from user_access_keys", nativeQuery = true)
    fun countLinks(): Long

    @Query(
        value = """
            select k.id as accessKeyId,
                   k.resource as resource,
                   k.description as description,
                   k.key_hint as keyHint,
                   k.is_active as isActive,
                   uak.linked_at as linkedAt
            from access_keys k
            join user_access_keys uak on uak.access_key_id = k.id
            where uak.user_id = :userId
            order by k.resource
        """,
        nativeQuery = true
    )
    fun findLinkedByUserId(@Param("userId") userId: Long): List<LinkedAccessKeyProjection>
}
