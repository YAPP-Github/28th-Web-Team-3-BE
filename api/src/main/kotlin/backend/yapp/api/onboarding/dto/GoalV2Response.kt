package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.domain.OnboardingGoal
import backend.yapp.core.onboarding.domain.OnboardingStatus

/** (v2) 목표 확정 응답. plan 개념이 제거되어 확정된 목표 금액·기간만 반환한다. */
data class GoalV2Response(
    val goalId: Long,
    val periodMonths: Int,
    val targetAmountManwon: Int,
    val status: OnboardingStatus,
) {
    companion object {
        fun from(goal: OnboardingGoal): GoalV2Response =
            GoalV2Response(
                goalId = goal.id,
                periodMonths = goal.periodMonths,
                targetAmountManwon = goal.targetAmountManwon,
                status = OnboardingStatus.COMPLETED,
            )
    }
}
