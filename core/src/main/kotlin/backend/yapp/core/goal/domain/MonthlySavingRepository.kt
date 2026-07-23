package backend.yapp.core.goal.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MonthlySavingRepository : JpaRepository<MonthlySaving, Long> {
    fun findByGuestUserIdAndYearMonth(guestUserId: Long, yearMonth: String): MonthlySaving?

    @Query("select coalesce(sum(m.savedAmountManwon), 0) from MonthlySaving m where m.guestUserId = :guestUserId")
    fun sumSavedByGuestUserId(@Param("guestUserId") guestUserId: Long): Long
}
