package backend.yapp.core.goal.service

/** 목표 현황 조회 결과. 금액 단위는 만원. */
data class GoalStatus(
    val targetAmountManwon: Int,
    val periodMonths: Int,
    val totalSavedManwon: Int,
    val progressPercent: Int,
    val usageMonths: Int,
    val deadlineDDay: Int,
    val thisMonth: ThisMonthStatus,
)

data class ThisMonthStatus(
    val targetManwon: Int,
    val savedManwon: Int,
    val progressPercent: Int,
    val dDay: Int,
)
