package backend.yapp.infra.onboarding

import backend.yapp.core.onboarding.port.Distribution
import backend.yapp.core.onboarding.port.DistributionBin
import backend.yapp.core.onboarding.port.FinanceStatistics
import backend.yapp.core.onboarding.port.FinanceStatisticsPort
import org.springframework.stereotype.Component

/**
 * 내장 통계 데이터셋: 2025년 가계금융복지조사, 29세 이하 가구 기준(단위 %).
 * 연 1회(매년 12월 통계 발표) 갱신 시 [DATASET_VERSION]과 분포 값을 함께 업데이트한다.
 * 개방 구간의 densityWidthManwon은 히스토그램 밀도 렌더링용 명목 폭이다.
 */
@Component
class FinanceStatisticsAdapter : FinanceStatisticsPort {
    private val statistics = FinanceStatistics(
        datasetVersion = DATASET_VERSION,
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

    override fun current(): FinanceStatistics = statistics

    companion object {
        private const val DATASET_VERSION = "gafinance-2025-u29"
    }
}
