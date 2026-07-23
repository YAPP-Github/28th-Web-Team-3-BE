package backend.yapp.core.onboarding.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OnboardingProfileRepository : JpaRepository<OnboardingProfile, Long> {
    fun findByGuestUserId(guestUserId: Long): OnboardingProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from OnboardingProfile profile where profile.guestUserId = :guestUserId")
    fun findByGuestUserIdForUpdate(@Param("guestUserId") guestUserId: Long): OnboardingProfile?
}
