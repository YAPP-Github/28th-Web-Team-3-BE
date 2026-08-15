package backend.yapp.api.onboarding.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/**
 * (v2) 목표 확정("이 목표로 시작") 요청. plan 개념 없이 슬라이더로 고른 매달 모을 금액만으로 확정한다.
 * 금액은 현재 월저축액 이상 ~ MIN(월저축액 × 1.5, 월급) 이하여야 한다(범위 밖이면 400).
 */
data class GoalConfirmV2Request(
    @field:Schema(description = "매달 모을 금액(만원). '얼마를 목표로 저축할까요?' 슬라이더로 선택한 값.", example = "115")
    @field:NotNull
    @field:Min(0) @field:Max(650)
    val monthlySavingManwon: Int,
)
