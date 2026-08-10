package backend.yapp.core.onboarding.service

import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate

/** 온보딩 스텝별 부분 저장 커맨드. null 필드는 "변경 없음"을 의미한다. */
data class ProfilePatchCommand(
    val birthDate: LocalDate? = null,
    val address: ResidentialArea? = null,
    val monthlySalaryManwon: Int? = null,
    val monthlySavingManwon: Int? = null,
    val netWorthManwon: Int? = null,
    val goalPeriodMonths: Int? = null,
)
