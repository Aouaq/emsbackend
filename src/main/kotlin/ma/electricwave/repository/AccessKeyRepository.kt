package ma.electricwave.repository

import ma.electricwave.domain.AccessKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface AccessKeyRepository : JpaRepository<AccessKey, Long> {

    fun findByKeyValue(keyValue: String): Optional<AccessKey>

    fun existsByKeyValue(keyValue: String): Boolean

    fun findAllByIsActiveTrueOrderByResource(): List<AccessKey>

    @Query(
        "select distinct k from AccessKey k left join fetch k.linkedUsers order by k.id"
    )
    fun findAllWithUsers(): List<AccessKey>
}
