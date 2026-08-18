package backend.yapp.core.onboarding.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingProfileService(
    private val profileRepository: OnboardingProfileRepository,
    private val goalRepository: GoalRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun patch(guestUserId: Long, command: ProfilePatchCommand): OnboardingProfile {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: OnboardingProfile(guestUserId = guestUserId)
        if (profile.status == OnboardingStatus.COMPLETED) {
            throw BaseException(ErrorCode.ONBOARDING_ALREADY_COMPLETED)
        }
        applyFields(profile, command)
        return profileRepository.save(profile)
    }

    /**
     * 내 정보 수정: 온보딩 완료 여부와 무관하게 프로필 값을 수정한다(스텝 저장용 patch와 달리 상태 제한 없음).
     * 저장된 프로필이 없으면 새로 만든다.
     */
    @Transactional
    fun update(guestUserId: Long, command: ProfilePatchCommand): OnboardingProfile {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: OnboardingProfile(guestUserId = guestUserId)
        applyFields(profile, command)
        val saved = profileRepository.save(profile)
        syncGoalFromProfile(saved)
        return saved
    }

    /**
     * 내 정보 수정 값을 확정된 목표(Goal)에 반영한다: 순자산→base, 목표기간→기간, 월저축액→매달 모을 금액.
     * 목표금액 = 순자산 + (매달 모을 금액 × 목표기간)으로 재계산한다.
     * 아직 목표가 없으면(온보딩 미확정) 아무것도 하지 않는다.
     */
    private fun syncGoalFromProfile(profile: OnboardingProfile) {
        val goal = goalRepository.findByGuestUserId(profile.guestUserId) ?: return
        val monthly = profile.monthlySavingManwon ?: goal.monthlyTargetManwon
        val period = profile.goalPeriodMonths ?: goal.periodMonths
        val base = profile.netWorthManwon ?: 0
        goal.baseAmountManwon = base
        goal.periodMonths = period
        goal.monthlyTargetManwon = monthly
        goal.targetAmountManwon = base + monthly * period
        goal.updatedAt = clock.instant()
        goalRepository.save(goal)
    }

    private fun applyFields(profile: OnboardingProfile, command: ProfilePatchCommand) {
        command.birthDate?.let { profile.birthDate = validateBirthDate(it) }
        command.address?.let { profile.address = it }
        command.monthlySalaryManwon?.let { profile.monthlySalaryManwon = validateRange(it, 0, MAX_MONEY_MANWON) }
        command.monthlySavingManwon?.let { profile.monthlySavingManwon = validateRange(it, 0, MAX_MONEY_MANWON) }
        command.netWorthManwon?.let { profile.netWorthManwon = validateRange(it, 0, MAX_NET_WORTH_MANWON) }
        command.goalPeriodMonths?.let { profile.goalPeriodMonths = validateRange(it, MIN_MONTHS, MAX_MONTHS) }
        validateSavingWithinSalary(profile)
        profile.updatedAt = clock.instant()
    }

    @Transactional(readOnly = true)
    fun get(guestUserId: Long): OnboardingProfile =
        profileRepository.findByGuestUserId(guestUserId)
            ?: OnboardingProfile(guestUserId = guestUserId)

    private fun validateRange(value: Int, min: Int, max: Int): Int {
        if (value < min || value > max) throw BaseException(ErrorCode.INVALID_ONBOARDING_INPUT)
        return value
    }

    private fun validateBirthDate(value: LocalDate): LocalDate {
        if (!value.isBefore(LocalDate.now(clock))) throw BaseException(ErrorCode.INVALID_ONBOARDING_INPUT)
        return value
    }

    /** 월저축액이 월급을 넘으면 소비가 음수가 되어 백분위가 무너지므로 입력 단계에서 막는다. */
    private fun validateSavingWithinSalary(profile: OnboardingProfile) {
        val salary = profile.monthlySalaryManwon ?: return
        val saving = profile.monthlySavingManwon ?: return
        if (saving > salary) throw BaseException(ErrorCode.INVALID_ONBOARDING_INPUT)
    }

    companion object {
        private const val MAX_MONEY_MANWON = 650
        private const val MAX_NET_WORTH_MANWON = 10_000
        private const val MIN_MONTHS = 3
        private const val MAX_MONTHS = 36
    }
}
