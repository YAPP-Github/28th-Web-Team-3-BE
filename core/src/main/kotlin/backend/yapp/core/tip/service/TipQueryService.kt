package backend.yapp.core.tip.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.bookmark.domain.ContentBookmarkRepository
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.tip.domain.BlogTip
import backend.yapp.core.tip.domain.BlogTipRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 절약 팁 목록 요약. */
data class TipSummary(
    val id: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val subcategory: String?,
    val sourceUrl: String?,
    val bookmarked: Boolean,
)

/** 절약 팁 상세. */
data class TipDetail(
    val id: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val subcategory: String?,
    val sourceUrl: String?,
    val bookmarked: Boolean,
)

@Service
class TipQueryService(
    private val tipRepository: BlogTipRepository,
    private val bookmarkRepository: ContentBookmarkRepository,
) {
    @Transactional(readOnly = true)
    fun list(guestUserId: Long, category: String?, subcategory: String?, page: Int, size: Int): List<TipSummary> {
        val tips = tipRepository
            .search(category?.ifBlank { null }, subcategory?.ifBlank { null }, PageRequest.of(page, size))
            .content
        val bookmarkedIds = bookmarkedIds(guestUserId, tips.map { it.id })
        return tips.map { it.toSummary(it.id in bookmarkedIds) }
    }

    @Transactional(readOnly = true)
    fun detail(guestUserId: Long, id: Long): TipDetail {
        val tip = tipRepository.findById(id).orElseThrow { BaseException(ErrorCode.TIP_NOT_FOUND) }
        val bookmarked = bookmarkRepository
            .existsByGuestUserIdAndContentTypeAndContentId(guestUserId, ContentType.TIP, id)
        return TipDetail(tip.id, tip.title, tip.description, tip.category, tip.subcategory, tip.sourceUrl, bookmarked)
    }

    private fun bookmarkedIds(guestUserId: Long, ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        return bookmarkRepository
            .findByGuestUserIdAndContentTypeAndContentIdIn(guestUserId, ContentType.TIP, ids)
            .map { it.contentId }
            .toSet()
    }

    private fun BlogTip.toSummary(bookmarked: Boolean) =
        TipSummary(id, title, description, category, subcategory, sourceUrl, bookmarked)
}
