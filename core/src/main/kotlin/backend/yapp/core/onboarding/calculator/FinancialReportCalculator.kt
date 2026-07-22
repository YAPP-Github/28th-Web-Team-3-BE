package backend.yapp.core.onboarding.calculator

import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.port.Distribution
import backend.yapp.core.onboarding.port.FinanceStatistics
import backend.yapp.core.onboarding.port.OnboardingConfig
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Onboarding_Report 계산기. 스펙의 시뮬레이션·백분위·히스토그램·종합 분석 로직을 담는다.
 * 스프링에 의존하지 않는 순수 계산기이며, 정책 값과 통계는 생성자로 주입받는다.
 *
 * 전제: [OnboardingProfile]의 월급·월저축·순자산·목표기간이 모두 채워져 있어야 한다(호출부에서 보장).
 */
class FinancialReportCalculator(
    private val config: OnboardingConfig,
    private val stats: FinanceStatistics,
) {
    fun calculate(profile: OnboardingProfile): FinancialReport {
        val salary = profile.monthlySalaryManwon!!
        val saving = profile.monthlySavingManwon!!
        val netWorth = profile.netWorthManwon!!
        val months = profile.goalPeriodMonths!!

        val simulation = simulate(saving, netWorth, months)
        // 입력 월급은 세후 실수령액. 소득 백분위는 세전 통계 기준이라 gross-up 보정계수로 세전 환산한다.
        val incomeManwon = (salary * 12 * config.salaryCorrectionFactor).roundToInt()
        // 소비는 세후 실수령에서 저축을 뺀 실제 지출이라 보정 없이 그대로 쓴다.
        val consumptionManwon = (salary - saving) * 12

        val incomePercentile = percentile(incomeManwon.toDouble(), stats.incomeDistribution)
        val consumptionPercentile = percentile(consumptionManwon.toDouble(), stats.consumptionDistribution)
        val assetRatioPercent = (netWorth.toDouble() / stats.netWorthMedianManwon * 100).roundToInt()

        val peer = PeerComparison(
            assetRatioPercent = assetRatioPercent,
            incomeTopPercent = topPercent(incomePercentile),
            consumptionTopPercent = topPercent(consumptionPercentile),
        )
        val histogram = Histogram(
            income = series(stats.incomeDistribution, incomeManwon),
            consumption = series(stats.consumptionDistribution, consumptionManwon),
        )
        val diagnosis = diagnose(
            netWorth = netWorth,
            saving = saving,
            incomePercentile = incomePercentile,
            consumptionPercentile = consumptionPercentile,
            assetRatioPercent = assetRatioPercent,
        )
        return FinancialReport(
            simulation = simulation,
            peer = peer,
            histogram = histogram,
            diagnosis = diagnosis,
            disclaimer = disclaimer(),
            datasetVersion = stats.datasetVersion,
            configVersion = config.version,
        )
    }

    private fun simulate(saving: Int, netWorth: Int, months: Int): Simulation {
        val uplift = config.reportUpliftPercent / 100.0
        val baseline = futureValue(saving.toDouble(), netWorth, months)
        val simulated = futureValue(saving * (1 + uplift), netWorth, months)
        val baselineManwon = floorManwon(baseline)
        val simulationManwon = floorManwon(simulated)
        return Simulation(
            baselineManwon = baselineManwon,
            simulationManwon = simulationManwon,
            diffManwon = simulationManwon - baselineManwon,
            upliftPercent = config.reportUpliftPercent,
            periodMonths = months,
        )
    }

    /** 순자산은 목표기간 전체를, 매월 저축액은 납입 시점부터 목표기간까지 연이율 r로 복리 성장시킨다. */
    private fun futureValue(monthlySaving: Double, netWorth: Int, months: Int): Double {
        val r = config.annualRate
        val base = netWorth * (1 + r).pow(months / 12.0)
        var accumulated = 0.0
        for (t in 1..months) {
            accumulated += monthlySaving * (1 + r).pow((months - t) / 12.0)
        }
        return base + accumulated
    }

    /** 구간 내 균등분포를 가정한 선형보간 백분위. 개방 구간은 하한에 캡(상대위치 0)한다. */
    private fun percentile(value: Double, distribution: Distribution): Double {
        val bins = distribution.bins
        val index = bins.indexOfLast { value >= it.lowerManwon }.coerceAtLeast(0)
        val bin = bins[index]
        val cumulativeLower = distribution.cumulativeLowerAt(index)
        val relative = if (bin.upperManwon == null) {
            0.0
        } else {
            ((value - bin.lowerManwon) / (bin.upperManwon - bin.lowerManwon)).coerceIn(0.0, 1.0)
        }
        return cumulativeLower + bin.ratio * relative
    }

    /** 상위 % = 100 - 백분위, 5%p 단위 반올림. */
    private fun topPercent(percentile: Double): Int {
        val top = 100.0 - percentile
        return (top / 5.0).roundToInt() * 5
    }

    private fun series(distribution: Distribution, markerManwon: Int): HistogramSeries =
        HistogramSeries(
            bins = distribution.bins.map {
                HistogramBin(
                    lowerManwon = it.lowerManwon,
                    upperManwon = it.upperManwon,
                    ratio = it.ratio,
                    density = it.ratio / (it.densityWidthManwon / 1000.0),
                )
            },
            markerManwon = markerManwon,
        )

    private fun diagnose(
        netWorth: Int,
        saving: Int,
        incomePercentile: Double,
        consumptionPercentile: Double,
        assetRatioPercent: Int,
    ): Diagnosis {
        val branch = DiagnosisBranch.of(
            assetHigh = netWorth >= stats.netWorthMedianManwon,
            incomeHigh = incomePercentile >= 50.0,
            consumptionHigh = consumptionPercentile >= 50.0,
        )
        val reductionManwon = (saving * 0.10).roundToInt()
        val intro = "지금 모은 %,d만원은 또래 중앙값 %,d만원의 %d%% 수준이에요.".format(
            netWorth, stats.netWorthMedianManwon, assetRatioPercent,
        )
        val action = branch.action.replace("{N}", reductionManwon.toString())
        return Diagnosis(
            branchCode = branch.code,
            message = "$intro ${branch.diagnosis} $action",
        )
    }

    private fun disclaimer(): String =
        "두 값 모두 연 %.1f%% 복리 적용. 단순 추정치일 뿐 정확하지 않을 수 있습니다.".format(config.annualRate * 100)

    /** 부동소수점 누적 오차(…9999)가 내림을 한 만원 깎지 않도록 미세 보정 후 내림한다. */
    private fun floorManwon(value: Double): Int = floor(value + FLOOR_EPSILON).toInt()

    companion object {
        private const val FLOOR_EPSILON = 1e-6
    }
}
