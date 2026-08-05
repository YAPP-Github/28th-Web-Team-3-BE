package backend.yapp.core.goal.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GoalRepository : JpaRepository<Goal, Long> {
    fun findByGuestUserId(guestUserId: Long): Goal?

    @Modifying
    @Query("delete from Goal goal where goal.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
}
