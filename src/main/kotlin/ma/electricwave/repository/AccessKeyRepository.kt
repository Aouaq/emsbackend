package ma.electricwave.repository

import ma.electricwave.domain.AccessKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface AccessKeyRepository : JpaRepository<AccessKey, Long> {

    fun findByKeyHash(keyHash: String): Optional<AccessKey>

    fun existsByKeyHash(keyHash: String): Boolean

    fun existsByResource(resource: String): Boolean

    fun findByResource(resource: String): Optional<AccessKey>

    fun findAllByIsActiveTrueOrderByResource(): List<AccessKey>

    @Query(
        "select distinct k from AccessKey k left join fetch k.linkedUsers order by k.id"
    )
    fun findAllWithUsers(): List<AccessKey>
}
