package backend.yapp.core.onboarding.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.onboarding.calculator.GoalPlanCalculator
import backend.yapp.core.onboarding.calculator.GoalPlans
import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.domain.OnboardingGoal
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.port.OnboardingConfigPort
import backend.yapp.core.goal.domain.Goal
import backend.yapp.core.goal.domain.GoalRepository
import java.time.Clock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingGoalService(
    private val profileRepository: OnboardingProfileRepository,
    private val onboardingGoalRepository: OnboardingGoalRepository,
    private val goalRepository: GoalRepository,
    private val configPort: OnboardingConfigPort,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional(readOnly = true)
    fun plans(guestUserId: Long): GoalPlans {
        val profile = readGoalReadyProfile(guestUserId)
        return GoalPlanCalculator(configPort.current())
            .calculate(profile.monthlySavingManwon!!, profile.goalPeriodMonths!!)
    }

    @Transactional
    fun confirm(guestUserId: Long, plan: GoalPlan): OnboardingGoal {
        val profile = readGoalReadyProfile(guestUserId)
        if (profile.status == OnboardingStatus.COMPLETED) {
            throw BaseException(ErrorCode.ONBOARDING_ALREADY_COMPLETED)
        }
        val config = configPort.current()
        val chosen = GoalPlanCalculator(config)
            .calculate(profile.monthlySavingManwon!!, profile.goalPeriodMonths!!)
            .of(plan)

        val onboardingGoal = onboardingGoalRepository.save(
            OnboardingGoal(
                guestUserId = guestUserId,
                plan = plan,
                periodMonths = profile.goalPeriodMonths!!,
                monthlySavingManwon = profile.monthlySavingManwon!!,
                upliftPermille = chosen.upliftPermille,
                targetAmountManwon = chosen.card.amountManwon,
                configVersion = config.version,
                createdAt = clock.instant(),
            ),
        )
        goalRepository.save(
            Goal(
                guestUserId = guestUserId,
                targetAmountManwon = onboardingGoal.targetAmountManwon,
                periodMonths = onboardingGoal.periodMonths,
                monthlyTargetManwon = onboardingGoal.monthlySavingManwon,
                baseAmountManwon = profile.netWorthManwon ?: 0,
                startedAt = onboardingGoal.createdAt,
            ),
        )
        profile.status = OnboardingStatus.COMPLETED
        profile.updatedAt = clock.instant()
        profileRepository.save(profile)
        return onboardingGoal
    }

    private fun readGoalReadyProfile(guestUserId: Long): OnboardingProfile {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND)
        if (!profile.isGoalReady()) throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)
        return profile
    }
}
