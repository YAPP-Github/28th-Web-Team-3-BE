package backend.yapp.core.onboarding.calculator

import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.port.OnboardingConfig
import backend.yapp.core.onboarding.port.PlanUplift
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalPlanCalculatorTest {
    private val config = OnboardingConfig(
        version = "test",
        annualRate = 0.03,
        reportUpliftPercent = 15,
        salaryCorrectionFactor = 1.0,
        plan1 = PlanUplift(min = 0.15, max = 0.30, single = 0.15),
        plan2 = PlanUplift(min = 0.05, max = 0.15, single = 0.05),
    )
    private val calculator = GoalPlanCalculator(config)

    @Test
    fun `plan1 checkpoints and range follow the spec example`() {
        val plan1 = calculator.calculate(monthlySaving = 100, periodMonths = 12).of(GoalPlan.PLAN_1)

        assertEquals(180, plan1.increaseMinManwon)
        assertEquals(360, plan1.increaseMaxManwon)
        assertEquals(
            listOf(3 to 345, 6 to 690, 9 to 1035, 12 to 1380),
            plan1.checkpoints.map { it.month to it.amountManwon },
        )
        assertEquals(12 to 1380, plan1.card.month to plan1.card.amountManwon)
        assertTrue(plan1.default)
    }

    @Test
    fun `plan2 uses the lower uplift band`() {
        val plan2 = calculator.calculate(monthlySaving = 100, periodMonths = 12).of(GoalPlan.PLAN_2)

        assertEquals(60, plan2.increaseMinManwon)
        assertEquals(180, plan2.increaseMaxManwon)
        assertEquals(1260, plan2.card.amountManwon) // 100 * 1.05 * 12
        assertEquals(false, plan2.default)
    }

    @Test
    fun `checkpoint months use ceil quartiles with the final month fixed to n`() {
        assertEquals(listOf(2, 4, 6, 7), monthsOf(7))
        assertEquals(listOf(5, 10, 15, 19), monthsOf(19))
    }

    @Test
    fun `n=3 collapses duplicate final checkpoints`() {
        assertEquals(listOf(1, 2, 3), monthsOf(3))
    }

    private fun monthsOf(periodMonths: Int): List<Int> =
        calculator.calculate(monthlySaving = 100, periodMonths = periodMonths)
            .of(GoalPlan.PLAN_1)
            .checkpoints
            .map { it.month }
}
