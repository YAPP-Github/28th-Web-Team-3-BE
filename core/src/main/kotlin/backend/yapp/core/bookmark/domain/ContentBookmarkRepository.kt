package backend.yapp.core.bookmark.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ContentBookmarkRepository : JpaRepository<ContentBookmark, Long> {
    fun findByGuestUserIdAndContentTypeAndContentId(
        guestUserId: Long,
        contentType: ContentType,
        contentId: Long,
    ): ContentBookmark?

    fun existsByGuestUserIdAndContentTypeAndContentId(
        guestUserId: Long,
        contentType: ContentType,
        contentId: Long,
    ): Boolean

    fun findByGuestUserIdAndContentTypeOrderByCreatedAtDesc(
        guestUserId: Long,
        contentType: ContentType,
    ): List<ContentBookmark>

    fun findByGuestUserIdOrderByCreatedAtDesc(guestUserId: Long): List<ContentBookmark>

    fun findByGuestUserIdAndContentTypeAndContentIdIn(
        guestUserId: Long,
        contentType: ContentType,
        contentIds: Collection<Long>,
    ): List<ContentBookmark>
}
