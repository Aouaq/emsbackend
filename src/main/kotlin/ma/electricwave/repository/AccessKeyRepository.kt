package ma.electricwave.repository

import ma.electricwave.domain.AccessKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

/** A single user-to-resource link, for batch-loading a page of users in one query. */
interface UserResourceProjection {
    fun getUserId(): Long
    fun getResource(): String
    fun getIsActive(): Boolean
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
        "select distinct k from AccessKey k left join fetch k.linkedUsers order by k.id"
    )
    fun findAllWithUsers(): List<AccessKey>

    /**
     * Flips `is_active` in a single statement and reports whether a row matched.
     *
     * The admin page previously read the key, negated the flag in memory and saved it
     * back, so two concurrent toggles both acted on the same observed state and one of
     * them was lost.
     */
    @Modifying
    @Query("update AccessKey k set k.isActive = not k.isActive, k.updatedAt = :now where k.id = :id")
    fun toggleActive(@Param("id") id: Long, @Param("now") now: Instant): Int

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

    /**
     * Resolves the linked resources for many users at once.
     *
     * Mapping `User.accessKeys` per row instead triggers a lazy load per user, so
     * rendering a 100-user page cost 101 queries. Callers must skip the call for an empty
     * id collection, since `in ()` is not valid SQL.
     */
    @Query(
        value = """
            select uak.user_id as userId,
                   k.resource as resource,
                   k.is_active as isActive
            from user_access_keys uak
            join access_keys k on k.id = uak.access_key_id
            where uak.user_id in (:userIds)
            order by k.resource
        """,
        nativeQuery = true
    )
    fun findResourcesByUserIds(@Param("userIds") userIds: Collection<Long>): List<UserResourceProjection>

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
