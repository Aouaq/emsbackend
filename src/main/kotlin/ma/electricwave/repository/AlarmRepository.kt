package ma.electricwave.repository

import ma.electricwave.domain.Alarm
import ma.electricwave.domain.AlarmKind
import ma.electricwave.domain.AlarmThreshold
import ma.electricwave.domain.AlarmThresholdSeed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface AlarmRepository : JpaRepository<Alarm, Long>, JpaSpecificationExecutor<Alarm> {

    /** Every open alarm, which is what the poller needs to decide if a crossing is new. */
    fun findAllByClearedAtIsNull(): List<Alarm>

    /** Open alarms, for the admin summary. */
    fun countByClearedAtIsNull(): Long

    fun countByClearedAtIsNullAndKind(kind: AlarmKind): Long

    fun findFirstByAccessKeyIdAndKindAndClearedAtIsNull(
        accessKeyId: Long,
        kind: AlarmKind
    ): Optional<Alarm>

    /**
     * A user's alarms, newest first.
     *
     * Joined through `user_access_keys` rather than filtered in memory: a client sees
     * only the sites they have claimed, and doing that in the query means an admin's
     * larger result set never passes through the application first.
     */
    @Query(
        value = """
            select a.*
            from alarms a
            join user_access_keys uak on uak.access_key_id = a.access_key_id
            where uak.user_id = :userId
            order by a.raised_at desc
            limit :limit
        """,
        nativeQuery = true
    )
    fun findForUser(
        @Param("userId") userId: Long,
        @Param("limit") limit: Int
    ): List<Alarm>

    /** Every alarm, newest first - admins see the whole fleet. */
    @Query(
        value = "select a.* from alarms a order by a.raised_at desc limit :limit",
        nativeQuery = true
    )
    fun findAllNewestFirst(@Param("limit") limit: Int): List<Alarm>
}

interface AlarmThresholdRepository : JpaRepository<AlarmThreshold, Long>

interface AlarmThresholdSeedRepository : JpaRepository<AlarmThresholdSeed, Long>
