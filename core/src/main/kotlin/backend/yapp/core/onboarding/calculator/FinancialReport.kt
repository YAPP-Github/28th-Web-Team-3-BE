package backend.yapp.core.onboarding.calculator

/** [FinancialReportCalculator] 산출 결과. 모든 금액 단위는 만원. */
data class FinancialReport(
    val simulation: Simulation,
    val peer: PeerComparison,
    val histogram: Histogram,
    val diagnosis: Diagnosis,
    val disclaimer: String,
    val datasetVersion: String,
    val configVersion: String,
)

data class Simulation(
    val baselineManwon: Int,
    val simulationManwon: Int,
    val diffManwon: Int,
    val upliftPercent: Int,
    val periodMonths: Int,
)

data class PeerComparison(
    val assetRatioPercent: Int,
    val incomeTopPercent: Int,
    val consumptionTopPercent: Int,
)

data class Histogram(
    val income: HistogramSeries,
    val consumption: HistogramSeries,
)

data class HistogramSeries(
    val bins: List<HistogramBin>,
    val markerManwon: Int,
)

data class HistogramBin(
    val lowerManwon: Int,
    val upperManwon: Int?,
    val ratio: Double,
    val density: Double,
)

data class Diagnosis(
    val branchCode: Int,
    val message: String,
)
