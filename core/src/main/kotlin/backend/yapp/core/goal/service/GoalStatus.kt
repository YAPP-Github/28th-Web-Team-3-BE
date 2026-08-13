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

/** 월별 저축 현황 항목(막대그래프용). 금액 단위는 만원. */
data class MonthlySavingStatus(
    val yearMonth: String,
    val savedManwon: Int,
    val current: Boolean,
)

/** 목표 상세(v2): 기존 현황 + 월별 저축 현황 시계열. */
data class GoalDetailV2(
    val status: GoalStatus,
    val monthlySavings: List<MonthlySavingStatus>,
)
