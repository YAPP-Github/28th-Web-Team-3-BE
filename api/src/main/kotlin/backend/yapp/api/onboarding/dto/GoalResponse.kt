package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.domain.OnboardingGoal
import backend.yapp.core.onboarding.domain.OnboardingStatus

data class GoalResponse(
    val goalId: Long,
    val plan: GoalPlan,
    val periodMonths: Int,
    val targetAmountManwon: Int,
    val status: OnboardingStatus,
) {
    companion object {
        fun from(goal: OnboardingGoal): GoalResponse =
            GoalResponse(
                goalId = goal.id,
                plan = goal.plan,
                periodMonths = goal.periodMonths,
                targetAmountManwon = goal.targetAmountManwon,
                status = OnboardingStatus.COMPLETED,
            )
    }
}
