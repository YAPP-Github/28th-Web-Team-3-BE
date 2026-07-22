package backend.yapp.core.onboarding.calculator

import backend.yapp.core.onboarding.domain.GoalPlan

/** [GoalPlanCalculator] 산출 결과. 모든 금액 단위는 만원. */
data class GoalPlans(
    val monthlySavingManwon: Int,
    val periodMonths: Int,
    val plans: List<GoalPlanResult>,
) {
    fun of(plan: GoalPlan): GoalPlanResult =
        plans.first { it.plan == plan }
}

data class GoalPlanResult(
    val plan: GoalPlan,
    val label: String,
    val default: Boolean,
    val upliftPermille: Int,
    val increaseMinManwon: Int,
    val increaseMaxManwon: Int,
    val checkpoints: List<GoalCheckpoint>,
    val card: GoalCheckpoint,
)

data class GoalCheckpoint(
    val month: Int,
    val amountManwon: Int,
)
