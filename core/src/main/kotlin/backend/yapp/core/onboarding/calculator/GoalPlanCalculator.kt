package backend.yapp.core.onboarding.calculator

import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.port.OnboardingConfig
import backend.yapp.core.onboarding.port.PlanUplift
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Onboarding_GoalSelect 계산기. 안(1안/2안)별 목표 증가분 범위, 기간별 체크포인트, 카드 값을 산출한다.
 * 복리 미적용·단순 선형 누적이며, 스프링에 의존하지 않는다.
 *
 * uplift 는 퍼밀(1/1000) 정수로 환산해 계산한다. 부동소수점 곱(100 × 0.15 × 12 = 179.999…)이
 * 내림에서 어긋나는 것을 막아 만원 단위 결과를 정확히 맞춘다.
 *
 * 전제: 월저축액 >= 0, 목표기간 n(개월) 3~36 (호출부에서 보장).
 */
class GoalPlanCalculator(
    private val config: OnboardingConfig,
) {
    fun calculate(monthlySaving: Int, periodMonths: Int): GoalPlans =
        GoalPlans(
            monthlySavingManwon = monthlySaving,
            periodMonths = periodMonths,
            plans = listOf(
                plan(GoalPlan.PLAN_1, "확실하게", default = true, monthlySaving, periodMonths, config.plan1),
                plan(GoalPlan.PLAN_2, "여유롭게", default = false, monthlySaving, periodMonths, config.plan2),
            ),
        )

    private fun plan(
        plan: GoalPlan,
        label: String,
        default: Boolean,
        saving: Int,
        months: Int,
        uplift: PlanUplift,
    ): GoalPlanResult {
        val singlePermille = toPermille(uplift.single)
        val checkpoints = checkpointMonths(months).map { month ->
            GoalCheckpoint(month = month, amountManwon = amountAt(saving, month, singlePermille))
        }
        return GoalPlanResult(
            plan = plan,
            label = label,
            default = default,
            upliftPermille = singlePermille,
            increaseMinManwon = increase(saving, months, toPermille(uplift.min)),
            increaseMaxManwon = increase(saving, months, toPermille(uplift.max)),
            checkpoints = checkpoints,
            card = checkpoints.last(),
        )
    }

    /** n을 4등분(올림)한 체크포인트 개월. checkpoint_4는 항상 n. 중복(작은 n)은 제거해 순서 유지. */
    private fun checkpointMonths(months: Int): List<Int> =
        listOf(
            ceil(months * 1.0 / 4).toInt(),
            ceil(months * 2.0 / 4).toInt(),
            ceil(months * 3.0 / 4).toInt(),
            months,
        ).distinct()

    /** 목표금액 = 월저축액 × (1 + uplift) × 개월수 (선형 누적, 만원 내림). */
    private fun amountAt(saving: Int, month: Int, upliftPermille: Int): Int =
        (saving.toLong() * (1000 + upliftPermille) * month / 1000).toInt()

    /** 목표 증가분 = 월저축액 × uplift × 개월수 (만원 내림). */
    private fun increase(saving: Int, months: Int, upliftPermille: Int): Int =
        (saving.toLong() * upliftPermille * months / 1000).toInt()

    private fun toPermille(uplift: Double): Int = (uplift * 1000).roundToInt()
}
