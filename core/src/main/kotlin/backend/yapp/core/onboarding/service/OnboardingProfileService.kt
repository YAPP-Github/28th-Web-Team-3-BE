package backend.yapp.core.onboarding.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingProfileService(
    private val profileRepository: OnboardingProfileRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun patch(guestUserId: Long, command: ProfilePatchCommand): OnboardingProfile {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: OnboardingProfile(guestUserId = guestUserId)

        command.birthDate?.let { profile.birthDate = validateBirthDate(it) }
        command.monthlySalaryManwon?.let { profile.monthlySalaryManwon = validateRange(it, 0, MAX_MONEY_MANWON) }
        command.monthlySavingManwon?.let { profile.monthlySavingManwon = validateRange(it, 0, MAX_MONEY_MANWON) }
        command.netWorthManwon?.let { profile.netWorthManwon = validateRange(it, 0, MAX_NET_WORTH_MANWON) }
        command.goalPeriodMonths?.let { profile.goalPeriodMonths = validateRange(it, MIN_MONTHS, MAX_MONTHS) }
        validateSavingWithinSalary(profile)

        profile.updatedAt = clock.instant()
        return profileRepository.save(profile)
    }

    @Transactional(readOnly = true)
    fun get(guestUserId: Long): OnboardingProfile =
        profileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND)

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
