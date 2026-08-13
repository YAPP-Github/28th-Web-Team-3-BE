package backend.yapp.api.goal.dto

import backend.yapp.core.goal.service.GoalDetailV2
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 목표 상세 조회 v2 응답. 기존 [GoalStatusResponse] 필드에 월별 저축 현황([monthlySavings])이 추가된다.
 */
data class GoalV2Response(
    val targetAmountManwon: Int,
    val periodMonths: Int,
    val totalSavedManwon: Int,
    val progressPercent: Int,
    val usageMonths: Int,
    val deadlineDDay: Int,
    val thisMonth: ThisMonthResponse,
    @get:Schema(description = "월별 저축 현황(목표 시작월~이번 달, 오름차순). 막대그래프용이며 이번 달 항목은 current=true.")
    val monthlySavings: List<MonthlySavingResponse>,
) {
    companion object {
        fun from(detail: GoalDetailV2): GoalV2Response {
            val status = detail.status
            return GoalV2Response(
                targetAmountManwon = status.targetAmountManwon,
                periodMonths = status.periodMonths,
                totalSavedManwon = status.totalSavedManwon,
                progressPercent = status.progressPercent,
                usageMonths = status.usageMonths,
                deadlineDDay = status.deadlineDDay,
                thisMonth = ThisMonthResponse(
                    targetManwon = status.thisMonth.targetManwon,
                    savedManwon = status.thisMonth.savedManwon,
                    progressPercent = status.thisMonth.progressPercent,
                    dDay = status.thisMonth.dDay,
                ),
                monthlySavings = detail.monthlySavings.map {
                    MonthlySavingResponse(it.yearMonth, it.savedManwon, it.current)
                },
            )
        }
    }
}

data class MonthlySavingResponse(
    @get:Schema(description = "연월(yyyy-MM)", example = "2026-08")
    val yearMonth: String,
    @get:Schema(description = "해당 달 저축액(만원). 미입력 달은 0.")
    val savedManwon: Int,
    @get:Schema(description = "이번 달 여부(막대 강조용)")
    val current: Boolean,
)
