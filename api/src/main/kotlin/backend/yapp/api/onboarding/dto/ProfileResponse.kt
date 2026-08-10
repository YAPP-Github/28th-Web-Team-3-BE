package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate

data class ProfileResponse(
    val status: OnboardingStatus,
    val birthDate: LocalDate?,
    val address: ResidentialArea?,
    val monthlySalaryManwon: Int?,
    val monthlySavingManwon: Int?,
    val netWorthManwon: Int?,
    val goalPeriodMonths: Int?,
) {
    companion object {
        fun from(profile: OnboardingProfile): ProfileResponse =
            ProfileResponse(
                status = profile.status,
                birthDate = profile.birthDate,
                address = profile.address,
                monthlySalaryManwon = profile.monthlySalaryManwon,
                monthlySavingManwon = profile.monthlySavingManwon,
                netWorthManwon = profile.netWorthManwon,
                goalPeriodMonths = profile.goalPeriodMonths,
            )
    }
}
