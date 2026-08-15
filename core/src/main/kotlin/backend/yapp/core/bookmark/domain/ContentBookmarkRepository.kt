package backend.yapp.core.bookmark.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContentBookmarkRepository : JpaRepository<ContentBookmark, Long> {
    @Modifying
    @Query("delete from ContentBookmark bookmark where bookmark.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int

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
