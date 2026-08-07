package backend.yapp.api.goal.dto

import backend.yapp.core.goal.service.GoalStatus
import io.swagger.v3.oas.annotations.media.Schema

data class GoalStatusResponse(
    val targetAmountManwon: Int,
    val periodMonths: Int,
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
