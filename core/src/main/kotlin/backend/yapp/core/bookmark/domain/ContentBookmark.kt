package backend.yapp.core.bookmark.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 저장(북마크). 혜택(정책)·팁 공통으로 `contentType` + `contentId`로 참조하는 다형 북마크이며,
 * 게스트·유형·콘텐츠 조합이 유일하다.
 */
@Entity
@Table(name = "content_bookmark")
class ContentBookmark(
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    val contentType: ContentType,
    @Column(name = "content_id", nullable = false)
    val contentId: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
