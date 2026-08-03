package backend.yapp.core.onboarding.service

import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingCompletionService(
    private val profileRepository: OnboardingProfileRepository,
) {
    @Transactional(readOnly = true)
    fun isCompleted(guestUserId: Long): Boolean =
        profileRepository.findByGuestUserId(guestUserId)?.status == OnboardingStatus.COMPLETED
}
