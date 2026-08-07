package backend.yapp.core.onboarding.service

import backend.yapp.core.goal.domain.GoalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingCompletionService(
    private val goalRepository: GoalRepository,
) {
    @Transactional(readOnly = true)
    fun isCompleted(guestUserId: Long): Boolean =
        goalRepository.findByGuestUserId(guestUserId) != null
}
