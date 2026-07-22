package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.calculator.GoalCheckpoint
import backend.yapp.core.onboarding.calculator.GoalPlanResult
import backend.yapp.core.onboarding.calculator.GoalPlans
import backend.yapp.core.onboarding.domain.GoalPlan

data class GoalPlansResponse(
    val monthlySavingManwon: Int,
    val periodMonths: Int,
    val plans: List<GoalPlanResponse>,
) {
    companion object {
        fun from(plans: GoalPlans): GoalPlansResponse =
            GoalPlansResponse(
                monthlySavingManwon = plans.monthlySavingManwon,
                periodMonths = plans.periodMonths,
                plans = plans.plans.map { GoalPlanResponse.from(it) },
            )
    }
}

data class GoalPlanResponse(
    val plan: GoalPlan,
    val label: String,
    val default: Boolean,
    val increaseMinManwon: Int,
    val increaseMaxManwon: Int,
    val checkpoints: List<CheckpointResponse>,
    val card: CheckpointResponse,
) {
    companion object {
        fun from(result: GoalPlanResult): GoalPlanResponse =
            GoalPlanResponse(
                plan = result.plan,
                label = result.label,
                default = result.default,
                increaseMinManwon = result.increaseMinManwon,
                increaseMaxManwon = result.increaseMaxManwon,
                checkpoints = result.checkpoints.map { CheckpointResponse.from(it) },
                card = CheckpointResponse.from(result.card),
            )
    }
}

data class CheckpointResponse(
    val month: Int,
    val amountManwon: Int,
) {
    companion object {
        fun from(checkpoint: GoalCheckpoint): CheckpointResponse =
            CheckpointResponse(month = checkpoint.month, amountManwon = checkpoint.amountManwon)
    }
}
