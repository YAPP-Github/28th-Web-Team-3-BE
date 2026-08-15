package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.domain.ResidentialArea
import backend.yapp.core.onboarding.service.ProfilePatchCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import java.time.LocalDate

/** 온보딩 스텝별 부분 저장 요청. 각 스텝에서 입력한 필드만 전송한다(모두 선택). */
data class ProfilePatchRequest(
    @field:Schema(description = "생년월일(YYYY-MM-DD). 온보딩 1/4 '나이' 스텝.", example = "1998-03-01")
    @field:Past
    val birthDate: LocalDate? = null,
    @field:Schema(
        description = "거주지역(16개 시·도, 광주 제외 — 2026 개편으로 광주는 전남에 통합). " +
            "허용 값: SEOUL(서울), GYEONGGI(경기), INCHEON(인천), BUSAN(부산), DAEGU(대구), DAEJEON(대전), " +
            "SEJONG(세종), ULSAN(울산), CHUNGNAM(충남), CHUNGBUK(충북), GYEONGNAM(경남), GYEONGBUK(경북), " +
            "JEONNAM(전남), JEONBUK(전북), GANGWON(강원), JEJU(제주). 생략 시 SEOUL로 저장된다.",
        example = "SEOUL",
        defaultValue = "SEOUL",
    )
    val address: ResidentialArea? = null,
    @field:Schema(description = "월급(세후 실수령액, 만원). 0~650. 온보딩 2/4 스텝.", example = "300")
    @field:Min(0) @field:Max(650)
    val monthlySalaryManwon: Int? = null,
    @field:Schema(description = "월 저축액(만원). 0~650이며 월급을 초과할 수 없다. 온보딩 2/4 스텝.", example = "82")
    @field:Min(0) @field:Max(650)
    val monthlySavingManwon: Int? = null,
    @field:Schema(description = "현재 순자산(투자·예/적금 총합, 만원). 0~10000(1억). 온보딩 3/4 스텝.", example = "1800")
    @field:Min(0) @field:Max(10_000)
    val netWorthManwon: Int? = null,
    @field:Schema(description = "목표 기간(개월). 3~36. 온보딩 4/4 스텝.", example = "24")
    @field:Min(3) @field:Max(36)
    val goalPeriodMonths: Int? = null,
) {
    fun toCommand(): ProfilePatchCommand =
        ProfilePatchCommand(
            birthDate = birthDate,
            address = address,
            monthlySalaryManwon = monthlySalaryManwon,
            monthlySavingManwon = monthlySavingManwon,
            netWorthManwon = netWorthManwon,
            goalPeriodMonths = goalPeriodMonths,
        )
}

/** 목표 확정("이 목표로 시작") 요청. monthlySavingManwon(슬라이더 값) 또는 plan 중 하나로 확정한다. */
data class GoalConfirmRequest(
    @field:Schema(description = "매달 모을 금액(만원). 슬라이더로 선택한 값. 지정 시 이 값으로 목표 확정.", example = "115")
    @field:Min(0) @field:Max(650)
    val monthlySavingManwon: Int? = null,
    @field:Schema(
        description = "(구) 확정할 목표안. monthlySavingManwon 미지정 시 이 안의 권장 상향폭으로 확정. PLAN_1=확실하게, PLAN_2=여유롭게.",
        example = "PLAN_1",
    )
    val plan: GoalPlan? = null,
)
