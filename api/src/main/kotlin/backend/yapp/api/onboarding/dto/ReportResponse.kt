package backend.yapp.api.onboarding.dto

import backend.yapp.core.onboarding.calculator.FinancialReport
import backend.yapp.core.onboarding.calculator.HistogramSeries

data class ReportResponse(
    val simulation: SimulationResponse,
    val peer: PeerResponse,
    val histogram: HistogramResponse,
    val diagnosis: DiagnosisResponse,
    val disclaimer: String,
    val datasetVersion: String,
    val configVersion: String,
) {
    companion object {
        fun from(report: FinancialReport): ReportResponse =
            ReportResponse(
                simulation = SimulationResponse(
                    baselineManwon = report.simulation.baselineManwon,
                    simulationManwon = report.simulation.simulationManwon,
                    diffManwon = report.simulation.diffManwon,
                    upliftPercent = report.simulation.upliftPercent,
                    periodMonths = report.simulation.periodMonths,
                ),
                peer = PeerResponse(
                    assetRatioPercent = report.peer.assetRatioPercent,
                    incomeTopPercent = report.peer.incomeTopPercent,
                    consumptionTopPercent = report.peer.consumptionTopPercent,
                ),
                histogram = HistogramResponse(
                    income = HistogramSeriesResponse.from(report.histogram.income),
                    consumption = HistogramSeriesResponse.from(report.histogram.consumption),
                ),
                diagnosis = DiagnosisResponse(
                    branchCode = report.diagnosis.branchCode,
                    message = report.diagnosis.message,
                ),
                disclaimer = report.disclaimer,
                datasetVersion = report.datasetVersion,
                configVersion = report.configVersion,
            )
    }
}

data class SimulationResponse(
    val baselineManwon: Int,
    val simulationManwon: Int,
    val diffManwon: Int,
    val upliftPercent: Int,
    val periodMonths: Int,
)

data class PeerResponse(
    val assetRatioPercent: Int,
    val incomeTopPercent: Int,
    val consumptionTopPercent: Int,
)

data class HistogramResponse(
    val income: HistogramSeriesResponse,
    val consumption: HistogramSeriesResponse,
)

data class HistogramSeriesResponse(
    val bins: List<HistogramBinResponse>,
    val markerManwon: Int,
) {
    companion object {
        fun from(series: HistogramSeries): HistogramSeriesResponse =
            HistogramSeriesResponse(
                bins = series.bins.map {
                    HistogramBinResponse(
                        lowerManwon = it.lowerManwon,
                        upperManwon = it.upperManwon,
                        ratio = it.ratio,
                        density = it.density,
                    )
                },
                markerManwon = series.markerManwon,
            )
    }
}

data class HistogramBinResponse(
    val lowerManwon: Int,
    val upperManwon: Int?,
    val ratio: Double,
    val density: Double,
)

data class DiagnosisResponse(
    val branchCode: Int,
    val message: String,
)
