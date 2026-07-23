package backend.yapp.api.goal.dto

import backend.yapp.core.goal.service.GoalStatus

data class GoalStatusResponse(
    val targetAmountManwon: Int,
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
    val dDay: Int,
)
