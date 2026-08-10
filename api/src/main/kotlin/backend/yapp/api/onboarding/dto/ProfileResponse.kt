package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.domain.ResidentialArea
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class ProfileResponse(
    val status: OnboardingStatus,
    val birthDate: LocalDate?,
    @field:Schema(
        description = "거주지역(16개 시·도, 광주 제외). " +
            "SEOUL(서울), GYEONGGI(경기), INCHEON(인천), BUSAN(부산), DAEGU(대구), DAEJEON(대전), SEJONG(세종), " +
            "ULSAN(울산), CHUNGNAM(충남), CHUNGBUK(충북), GYEONGNAM(경남), GYEONGBUK(경북), JEONNAM(전남), " +
            "JEONBUK(전북), GANGWON(강원), JEJU(제주).",
    )
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
