package backend.yapp.core.onboarding.port

/**
 * 온보딩 계산에 쓰이는 정책 값. 금리(annualRate)·저축률 상향폭 등은 하드코딩 금지 대상이라
 * 외부 설정(Remote Config)에서 주입받는다. [OnboardingConfigPort] 구현은 infra 모듈에 둔다.
 */
data class OnboardingConfig(
    val version: String,
    val annualRate: Double,
    val reportUpliftPercent: Int,
    val salaryCorrectionFactor: Double,
    val plan1: PlanUplift,
    val plan2: PlanUplift,
) {
    fun upliftOf(plan: backend.yapp.core.onboarding.domain.GoalPlan): PlanUplift =
        when (plan) {
            backend.yapp.core.onboarding.domain.GoalPlan.PLAN_1 -> plan1
            backend.yapp.core.onboarding.domain.GoalPlan.PLAN_2 -> plan2
        }
}

data class PlanUplift(
    val min: Double,
    val max: Double,
    val single: Double,
)

interface OnboardingConfigPort {
    fun current(): OnboardingConfig
}
