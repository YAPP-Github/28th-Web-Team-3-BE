package backend.yapp.core.goal.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.goal.domain.Goal
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 추적용 [Goal]을 온보딩 확정 데이터로부터 지연 생성한다. 동시 최초 접근 시 유일성 위반을 흡수할 수 있도록
 * 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다.
 */
@Component
class GoalInitializer(
    private val goalRepository: GoalRepository,
    private val onboardingGoalRepository: OnboardingGoalRepository,
    private val onboardingProfileRepository: OnboardingProfileRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createIfAbsent(guestUserId: Long): Goal {
        goalRepository.findByGuestUserId(guestUserId)?.let { return it }

        val onboardingGoal = onboardingGoalRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.GOAL_ONBOARDING_REQUIRED)
        val profile = onboardingProfileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.GOAL_ONBOARDING_REQUIRED)

        return goalRepository.saveAndFlush(
            Goal(
                guestUserId = guestUserId,
                targetAmountManwon = onboardingGoal.targetAmountManwon,
                periodMonths = onboardingGoal.periodMonths,
                monthlyTargetManwon = onboardingGoal.monthlySavingManwon,
                baseAmountManwon = profile.netWorthManwon ?: 0,
                startedAt = onboardingGoal.createdAt,
            ),
        )
    }
}
