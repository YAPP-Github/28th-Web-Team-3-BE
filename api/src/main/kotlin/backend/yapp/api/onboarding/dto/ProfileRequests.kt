package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.service.ProfilePatchCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import java.time.LocalDate

/** 온보딩 스텝별 부분 저장 요청. 각 스텝에서 입력한 필드만 전송한다(모두 선택). */
data class ProfilePatchRequest(
    @field:Past
    val birthDate: LocalDate? = null,
    @field:Min(0) @field:Max(650)
    val monthlySalaryManwon: Int? = null,
    @field:Min(0) @field:Max(650)
    val monthlySavingManwon: Int? = null,
    @field:Min(0) @field:Max(10_000)
    val netWorthManwon: Int? = null,
    @field:Min(3) @field:Max(36)
    val goalPeriodMonths: Int? = null,
) {
    fun toCommand(): ProfilePatchCommand =
        ProfilePatchCommand(
            birthDate = birthDate,
            monthlySalaryManwon = monthlySalaryManwon,
            monthlySavingManwon = monthlySavingManwon,
            netWorthManwon = netWorthManwon,
            goalPeriodMonths = goalPeriodMonths,
        )
}

/** 목표 확정("이 목표로 시작") 요청. */
data class GoalConfirmRequest(
    @field:NotNull
    val plan: GoalPlan,
)
