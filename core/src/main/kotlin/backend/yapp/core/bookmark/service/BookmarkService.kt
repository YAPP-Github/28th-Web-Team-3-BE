package backend.yapp.core.bookmark.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.bookmark.domain.ContentBookmark
import backend.yapp.core.bookmark.domain.ContentBookmarkRepository
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.policy.domain.YouthPolicyRepository
import backend.yapp.core.tip.domain.BlogTipRepository
import java.time.Clock
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 저장(북마크) 목록 항목. 혜택·팁 공통 표현. */
data class SavedContent(
    val contentType: ContentType,
    val id: Long,
    val title: String,
    val category: String?,
    val description: String?,
)

@Service
class BookmarkService(
    private val bookmarkRepository: ContentBookmarkRepository,
    private val policyRepository: YouthPolicyRepository,
    private val tipRepository: BlogTipRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** 저장. 이미 저장돼 있으면 멱등하게 무시한다. */
    @Transactional
    fun add(guestUserId: Long, contentType: ContentType, contentId: Long) {
        requireContentExists(contentType, contentId)
        if (bookmarkRepository.existsByGuestUserIdAndContentTypeAndContentId(guestUserId, contentType, contentId)) {
            return
        }
        try {
            bookmarkRepository.save(
                ContentBookmark(
                    guestUserId = guestUserId,
                    contentType = contentType,
                    contentId = contentId,
                    createdAt = clock.instant(),
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            // 동시 저장 경합은 이미 저장된 것으로 간주(멱등)
        }
    }

    @Transactional
    fun remove(guestUserId: Long, contentType: ContentType, contentId: Long) {
        bookmarkRepository
            .findByGuestUserIdAndContentTypeAndContentId(guestUserId, contentType, contentId)
            ?.let { bookmarkRepository.delete(it) }
    }

    /** 저장됨 목록. 유형(선택)·카테고리(선택) 필터, 저장 최신순. */
    @Transactional(readOnly = true)
    fun saved(guestUserId: Long, contentType: ContentType?, category: String?): List<SavedContent> {
        val bookmarks = if (contentType != null) {
            bookmarkRepository.findByGuestUserIdAndContentTypeOrderByCreatedAtDesc(guestUserId, contentType)
        } else {
            bookmarkRepository.findByGuestUserIdOrderByCreatedAtDesc(guestUserId)
        }
        val policies = policyRepository
            .findAllById(bookmarks.filter { it.contentType == ContentType.POLICY }.map { it.contentId })
            .associateBy { it.id }
        val tips = tipRepository
            .findAllById(bookmarks.filter { it.contentType == ContentType.TIP }.map { it.contentId })
            .associateBy { it.id }

        return bookmarks.mapNotNull { bookmark ->
            when (bookmark.contentType) {
                ContentType.POLICY -> policies[bookmark.contentId]?.takeIf { category.matches(it.largeCategory) }
                    ?.let { SavedContent(ContentType.POLICY, it.id, it.title, it.largeCategory, it.description) }
                ContentType.TIP -> tips[bookmark.contentId]?.takeIf { category.matches(it.category) }
                    ?.let { SavedContent(ContentType.TIP, it.id, it.title, it.category, it.description) }
            }
        }
    }

    private fun requireContentExists(contentType: ContentType, contentId: Long) {
        val exists = when (contentType) {
            ContentType.POLICY -> policyRepository.existsById(contentId)
            ContentType.TIP -> tipRepository.existsById(contentId)
        }
        if (!exists) {
            val error = if (contentType == ContentType.POLICY) ErrorCode.POLICY_NOT_FOUND else ErrorCode.TIP_NOT_FOUND
            throw BaseException(error)
        }
    }

    private fun String?.matches(value: String?): Boolean =
        this.isNullOrBlank() || (value?.contains(this) ?: false)
}
