package backend.yapp.core.onboarding.port

/**
 * 또래 비교에 쓰이는 내장 통계 데이터셋(가계금융복지조사 기반). 연 1회 갱신되므로
 * 버전을 함께 노출하고, 계산 결과 저장 시 이 버전을 스냅샷해 재현성을 확보한다.
 */
data class FinanceStatistics(
    val datasetVersion: String,
    val netWorthMedianManwon: Int,
    val incomeDistribution: Distribution,
    val consumptionDistribution: Distribution,
)

data class Distribution(
    val bins: List<DistributionBin>,
) {
    /** 각 구간의 하위 누적 비율(구간 시작 지점 기준). */
    fun cumulativeLowerAt(index: Int): Double =
        bins.take(index).sumOf { it.ratio }
}

/**
 * @param upperManwon null 이면 상한이 없는 개방 구간.
 * @param densityWidthManwon 히스토그램 밀도 계산에 쓰는 폭. 개방 구간은 임의의 명목 폭을 데이터로 지정한다.
 */
data class DistributionBin(
    val lowerManwon: Int,
    val upperManwon: Int?,
    val ratio: Double,
    val densityWidthManwon: Int,
)

interface FinanceStatisticsPort {
    fun current(): FinanceStatistics
}
