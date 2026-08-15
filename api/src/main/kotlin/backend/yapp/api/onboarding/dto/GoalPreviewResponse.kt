package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.service.GoalPreview
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 목표 저축 미리보기 응답('얼마를 목표로 저축할까요?' 화면). 금액 단위는 만원.
 * expectedAmountManwon = baseAmountManwon(순자산) + additionalSavingManwon(매달 모을 금액 × 개월).
 */
data class GoalPreviewResponse(
    @get:Schema(description = "선택한 매달 모을 금액")
    val monthlySavingManwon: Int,
    @get:Schema(description = "현재(온보딩 입력) 월 저축액")
    val currentMonthlySavingManwon: Int,
    @get:Schema(description = "슬라이더 최소값(= 현재 저축액)")
    val minMonthlySavingManwon: Int,
    @get:Schema(description = "슬라이더 최대값")
    val maxMonthlySavingManwon: Int,
    @get:Schema(description = "슬라이더 기본 위치(현재 + 권장 상향폭)")
    val recommendedMonthlySavingManwon: Int,
    val periodMonths: Int,
    @get:Schema(description = "순자산")
    val baseAmountManwon: Int,
    @get:Schema(description = "추가 저축액(매달 모을 금액 × 개월)")
    val additionalSavingManwon: Int,
    @get:Schema(description = "저축 예상 금액(순자산 + 추가 저축액)")
    val expectedAmountManwon: Int,
    @get:Schema(description = "현재 대비 매달 더 모으는 금액")
    val extraMonthlyManwon: Int,
    @get:Schema(description = "현재 대비 상향 비율(%)")
    val extraPercent: Int,
) {
    companion object {
        fun from(p: GoalPreview): GoalPreviewResponse =
            GoalPreviewResponse(
                monthlySavingManwon = p.monthlySavingManwon,
                currentMonthlySavingManwon = p.currentMonthlySavingManwon,
                minMonthlySavingManwon = p.minMonthlySavingManwon,
                maxMonthlySavingManwon = p.maxMonthlySavingManwon,
                recommendedMonthlySavingManwon = p.recommendedMonthlySavingManwon,
                periodMonths = p.periodMonths,
                baseAmountManwon = p.baseAmountManwon,
                additionalSavingManwon = p.additionalSavingManwon,
                expectedAmountManwon = p.expectedAmountManwon,
                extraMonthlyManwon = p.extraMonthlyManwon,
                extraPercent = p.extraPercent,
            )
    }
}
