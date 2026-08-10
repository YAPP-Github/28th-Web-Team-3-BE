package backend.yapp.core.mission.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "mission_blog_tip")
class MissionBlogTip(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", nullable = false, length = 40)
    var item: MissionItem,
    @Column(name = "title", nullable = false, length = 300)
    var title: String,
    @Column(name = "source", nullable = false, length = 200)
    var source: String,
    @Column(name = "url", nullable = false, length = 1000)
    val url: String,
    @Column(name = "searched_at", nullable = false)
    var searchedAt: Instant,
)

interface MissionBlogTipRepository : JpaRepository<MissionBlogTip, UUID> {
    fun findByGuestUserIdAndUrl(guestUserId: Long, url: String): MissionBlogTip?
    fun deleteByGuestUserId(guestUserId: Long): Int
}
