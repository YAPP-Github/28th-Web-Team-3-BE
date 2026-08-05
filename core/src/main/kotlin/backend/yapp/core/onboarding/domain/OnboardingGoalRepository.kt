package backend.yapp.core.onboarding.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OnboardingGoalRepository : JpaRepository<OnboardingGoal, Long> {
    fun findByGuestUserId(guestUserId: Long): OnboardingGoal?

    fun deleteByGuestUserId(guestUserId: Long): Long
}
