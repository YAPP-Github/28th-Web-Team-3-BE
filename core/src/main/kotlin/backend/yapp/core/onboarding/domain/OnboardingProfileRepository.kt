package backend.yapp.core.onboarding.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OnboardingProfileRepository : JpaRepository<OnboardingProfile, Long> {
    fun findByGuestUserId(guestUserId: Long): OnboardingProfile?
}
