package backend.yapp.api.tip.dto

import backend.yapp.core.tip.service.TipDetail
import backend.yapp.core.tip.service.TipSummary
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "절약 팁 목록 항목")
data class TipSummaryResponse(
    val id: Long,
    @get:Schema(description = "팁 제목", example = "집밥 레시피 활용팁")
    val title: String,
    @get:Schema(description = "팁 요약(핵심 절약 방법)", example = "배달 메뉴 대신 집에서 직접 만드는 레시피 찾아보기")
    val description: String?,
    @get:Schema(description = "카테고리(식비/생활/취미)", example = "식비")
    val category: String?,
    @get:Schema(description = "선택항목(세부 분류)", example = "배달음식")
    val subcategory: String?,
    @get:Schema(description = "원문 링크(블로그/영상 등)")
    val sourceUrl: String?,
    @get:Schema(description = "현재 게스트의 저장(북마크) 여부")
    val bookmarked: Boolean,
) {
    companion object {
        fun from(summary: TipSummary) = TipSummaryResponse(
            summary.id,
            summary.title,
            summary.description,
            summary.category,
            summary.subcategory,
            summary.sourceUrl,
            summary.bookmarked,
        )
    }
}

@Schema(description = "절약 팁 상세")
data class TipDetailResponse(
    val id: Long,
    val title: String,
    @get:Schema(description = "팁 상세 내용")
    val description: String?,
    @get:Schema(description = "카테고리(식비/생활/취미)")
    val category: String?,
    @get:Schema(description = "선택항목(세부 분류)")
    val subcategory: String?,
    @get:Schema(description = "원문 링크(블로그/영상 등)")
    val sourceUrl: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(detail: TipDetail) = TipDetailResponse(
            detail.id,
            detail.title,
            detail.description,
            detail.category,
            detail.subcategory,
            detail.sourceUrl,
            detail.bookmarked,
        )
    }
}
