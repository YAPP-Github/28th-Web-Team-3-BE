package backend.yapp.api.tip.dto

import backend.yapp.core.tip.service.TipDetail
import backend.yapp.core.tip.service.TipSummary

data class TipSummaryResponse(
    val id: Long,
    val title: String,
    val category: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(summary: TipSummary) = TipSummaryResponse(summary.id, summary.title, summary.category, summary.bookmarked)
    }
}

data class TipDetailResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(detail: TipDetail) =
            TipDetailResponse(detail.id, detail.title, detail.description, detail.category, detail.bookmarked)
    }
}
