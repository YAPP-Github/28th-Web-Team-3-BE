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
import backend.yapp.core.onboarding.port.OnboardingConfig
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

    /**
     * 목표 저축 미리보기(슬라이더). [requestedMonthly] 가 null이면 권장값(현재 + 권장 상향폭)으로 계산한다.
     * 예상 금액 = 순자산 + 매달 모을 금액 × 개월.
     */
    @Transactional(readOnly = true)
    fun preview(guestUserId: Long, requestedMonthly: Int?): GoalPreview {
        val profile = readGoalReadyProfile(guestUserId)
        val config = configPort.current()
        val current = profile.monthlySavingManwon!!
        val chosen = resolveMonthly(requestedMonthly, current, config)
        return buildPreview(profile, chosen, config)
    }

    /**
     * 목표 확정("이 목표로 시작"). [monthlySavingManwon](슬라이더 값)이 있으면 그 금액으로,
     * 없으면 [plan] 의 권장 상향폭으로 매달 모을 금액을 정한다. 목표 금액에는 순자산을 포함한다.
     */
    @Transactional
    fun confirm(guestUserId: Long, plan: GoalPlan?, monthlySavingManwon: Int?): OnboardingGoal {
        val profile = readGoalReadyProfile(guestUserId)
        if (profile.status == OnboardingStatus.COMPLETED) {
            throw BaseException(ErrorCode.ONBOARDING_ALREADY_COMPLETED)
        }
        val config = configPort.current()
        val current = profile.monthlySavingManwon!!
        val months = profile.goalPeriodMonths!!
        val base = profile.netWorthManwon ?: 0

        val chosenMonthly = when {
            monthlySavingManwon != null -> resolveMonthly(monthlySavingManwon, current, config)
            plan != null -> applyRate(current, config.upliftOf(plan).single)
            else -> throw BaseException(ErrorCode.INVALID_ONBOARDING_INPUT)
        }
        val upliftPermille = if (current > 0) ((chosenMonthly - current).toLong() * 1000 / current).toInt() else 0
        val targetAmount = base + chosenMonthly * months

        val onboardingGoal = onboardingGoalRepository.save(
            OnboardingGoal(
                guestUserId = guestUserId,
                plan = plan ?: GoalPlan.PLAN_1,
                periodMonths = months,
                monthlySavingManwon = chosenMonthly,
                upliftPermille = upliftPermille,
                targetAmountManwon = targetAmount,
                configVersion = config.version,
                createdAt = clock.instant(),
            ),
        )
        goalRepository.save(
            Goal(
                guestUserId = guestUserId,
                targetAmountManwon = targetAmount,
                periodMonths = months,
                monthlyTargetManwon = chosenMonthly,
                baseAmountManwon = base,
                startedAt = onboardingGoal.createdAt,
            ),
        )
        profile.status = OnboardingStatus.COMPLETED
        profile.updatedAt = clock.instant()
        profileRepository.save(profile)
        return onboardingGoal
    }

    private fun buildPreview(profile: OnboardingProfile, chosen: Int, config: OnboardingConfig): GoalPreview {
        val current = profile.monthlySavingManwon!!
        val months = profile.goalPeriodMonths!!
        val base = profile.netWorthManwon ?: 0
        val additional = chosen * months
        return GoalPreview(
            monthlySavingManwon = chosen,
            currentMonthlySavingManwon = current,
            minMonthlySavingManwon = current,
            maxMonthlySavingManwon = applyRate(current, SLIDER_MAX_EXTRA_RATE),
            recommendedMonthlySavingManwon = applyRate(current, config.plan1.single),
            periodMonths = months,
            baseAmountManwon = base,
            additionalSavingManwon = additional,
            expectedAmountManwon = base + additional,
            extraMonthlyManwon = chosen - current,
            extraPercent = if (current > 0) Math.round((chosen - current) * 100.0 / current).toInt() else 0,
        )
    }

    /** 매달 모을 금액 결정: null이면 권장값. 현재 저축액 미만이거나 슬라이더 최대 초과면 400. */
    private fun resolveMonthly(requested: Int?, current: Int, config: OnboardingConfig): Int {
        val chosen = requested ?: applyRate(current, config.plan1.single)
        val max = applyRate(current, SLIDER_MAX_EXTRA_RATE)
        if (chosen < current || chosen > max) throw BaseException(ErrorCode.INVALID_ONBOARDING_INPUT)
        return chosen
    }

    /** value × (1 + rate) 를 만원 단위로 반올림 없이 퍼밀 정수 연산으로 계산. */
    private fun applyRate(value: Int, rate: Double): Int {
        val permille = Math.round(rate * 1000).toInt()
        return value + (value.toLong() * permille / 1000).toInt()
    }

    private fun readGoalReadyProfile(guestUserId: Long): OnboardingProfile {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND)
        if (!profile.isGoalReady()) throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)
        return profile
    }

    companion object {
        /** 슬라이더 최대 상향폭(현재 저축액 대비). 디자인 기준 +50%. */
        private const val SLIDER_MAX_EXTRA_RATE = 0.5
    }
}
