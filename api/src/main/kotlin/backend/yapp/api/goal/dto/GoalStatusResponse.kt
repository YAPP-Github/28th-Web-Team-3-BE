package backend.yapp.api.goal.dto

import backend.yapp.core.goal.service.GoalStatus
import io.swagger.v3.oas.annotations.media.Schema

data class GoalStatusResponse(
    val targetAmountManwon: Int,
    val periodMonths: Int,
    @get:Schema(description = "순자산(목표액에 포함). totalSavedManwon에 이미 반영된 값이다.")
    val baseAmountManwon: Int,
    @get:Schema(description = "총 모은 금액 = 순자산 + 월별 저축액 합. 진행률(progressPercent)의 분자.")
    val totalSavedManwon: Int,
    val progressPercent: Int,
    val usageMonths: Int,
    val deadlineDDay: Int,
    val thisMonth: ThisMonthResponse,
) {
    companion object {
        fun from(status: GoalStatus): GoalStatusResponse =
            GoalStatusResponse(
                targetAmountManwon = status.targetAmountManwon,
                periodMonths = status.periodMonths,
                baseAmountManwon = status.baseAmountManwon,
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
            )
    }
}

data class ThisMonthResponse(
    val targetManwon: Int,
    val savedManwon: Int,
    val progressPercent: Int,
    @get:Schema(name = "dDay")
    val dDay: Int,
)
