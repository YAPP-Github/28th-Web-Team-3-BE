package backend.yapp.core.goal.domain

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SavingRecordRepository : JpaRepository<SavingRecord, Long> {
    @Query("select coalesce(sum(s.amountManwon), 0) from SavingRecord s where s.guestUserId = :guestUserId")
    fun sumAmountByGuestUserId(@Param("guestUserId") guestUserId: Long): Long

    @Query(
        """
        select coalesce(sum(s.amountManwon), 0) from SavingRecord s
        where s.guestUserId = :guestUserId
          and s.recordedAt >= :from
          and s.recordedAt < :to
        """,
    )
    fun sumAmountInRange(
        @Param("guestUserId") guestUserId: Long,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long
}
