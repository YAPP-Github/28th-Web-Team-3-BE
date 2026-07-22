package backend.yapp.core.onboarding.calculator

import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.port.Distribution
import backend.yapp.core.onboarding.port.DistributionBin
import backend.yapp.core.onboarding.port.FinanceStatistics
import backend.yapp.core.onboarding.port.OnboardingConfig
import backend.yapp.core.onboarding.port.PlanUplift
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialReportCalculatorTest {
    private val stats = FinanceStatistics(
        datasetVersion = "test-2025",
        netWorthMedianManwon = 5_000,
        incomeDistribution = Distribution(
            listOf(
                DistributionBin(0, 1_000, 4.9, 1_000),
                DistributionBin(1_000, 3_000, 34.1, 2_000),
                DistributionBin(3_000, 5_000, 37.5, 2_000),
                DistributionBin(5_000, 7_000, 12.4, 2_000),
                DistributionBin(7_000, 10_000, 6.4, 3_000),
                DistributionBin(10_000, null, 4.7, 3_000),
            ),
        ),
        consumptionDistribution = Distribution(
            listOf(
                DistributionBin(0, 500, 0.3, 500),
                DistributionBin(500, 1_000, 4.4, 500),
                DistributionBin(1_000, 1_500, 21.9, 500),
                DistributionBin(1_500, 2_000, 26.7, 500),
                DistributionBin(2_000, 3_000, 30.1, 1_000),
                DistributionBin(3_000, 5_000, 14.7, 2_000),
                DistributionBin(5_000, null, 1.9, 2_000),
            ),
        ),
    )

    private fun config(annualRate: Double, correctionFactor: Double = 1.0) = OnboardingConfig(
        version = "test",
        annualRate = annualRate,
        reportUpliftPercent = 15,
        salaryCorrectionFactor = correctionFactor,
        plan1 = PlanUplift(0.15, 0.30, 0.15),
        plan2 = PlanUplift(0.05, 0.15, 0.05),
    )

    private fun profile() = OnboardingProfile(
        guestUserId = 1,
        monthlySalaryManwon = 350,
        monthlySavingManwon = 100,
        netWorthManwon = 1_800,
        goalPeriodMonths = 24,
    )

    @Test
    fun `simulation difference comes purely from the uplift`() {
        // annualRate=0 이면 미래가치 = 순자산 + 월저축 * 개월수 로 결정적이다.
        val report = FinancialReportCalculator(config(annualRate = 0.0), stats).calculate(profile())

        assertEquals(4_200, report.simulation.baselineManwon) // 1800 + 100*24
        assertEquals(4_560, report.simulation.simulationManwon) // 1800 + 115*24
        assertEquals(360, report.simulation.diffManwon)
        assertEquals(15, report.simulation.upliftPercent)
    }

    @Test
    fun `income percentile matches the spec example`() {
        val report = FinancialReportCalculator(config(annualRate = 0.03), stats).calculate(profile())

        // 보정계수 1.0(세전 그대로) 기준, 연소득 4,200만 -> 상위 약 40%
        assertEquals(40, report.peer.incomeTopPercent)
        assertEquals(36, report.peer.assetRatioPercent) // 1800 / 5000 * 100
    }

    @Test
    fun `salary correction factor grosses up net income for the percentile`() {
        // 세후 월 350만 -> 세전 환산 4,830만(×1.15) -> 상위 약 25%
        val report = FinancialReportCalculator(config(annualRate = 0.03, correctionFactor = 1.15), stats).calculate(profile())

        assertEquals(25, report.peer.incomeTopPercent)
    }

    @Test
    fun `diagnosis embeds asset ratio intro and disclaimer reflects the rate`() {
        val report = FinancialReportCalculator(config(annualRate = 0.03), stats).calculate(profile())

        assertTrue(report.diagnosis.message.startsWith("지금 모은 1,800만원은 또래 중앙값 5,000만원의 36% 수준이에요."))
        assertEquals("두 값 모두 연 3.0% 복리 적용. 단순 추정치일 뿐 정확하지 않을 수 있습니다.", report.disclaimer)
    }

    @Test
    fun `open ended top income bin caps at its lower bound`() {
        val richProfile = OnboardingProfile(
            guestUserId = 2,
            monthlySalaryManwon = 650,
            monthlySavingManwon = 0,
            netWorthManwon = 9_000,
            goalPeriodMonths = 12,
        )
        // 연소득 7,800만 -> (7000~10000 구간) 상위 약 10%
        val report = FinancialReportCalculator(config(annualRate = 0.03), stats).calculate(richProfile)
        assertEquals(10, report.peer.incomeTopPercent)
    }
}
