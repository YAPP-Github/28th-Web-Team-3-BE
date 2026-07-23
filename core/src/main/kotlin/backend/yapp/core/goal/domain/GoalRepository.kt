package backend.yapp.core.goal.domain

import org.springframework.data.jpa.repository.JpaRepository

interface GoalRepository : JpaRepository<Goal, Long> {
    fun findByGuestUserId(guestUserId: Long): Goal?
}
