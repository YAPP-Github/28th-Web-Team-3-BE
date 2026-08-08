package backend.yapp.core.tip.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 블로그 팁(절약 팁 등). 콘텐츠 데이터 소스는 후속 작업으로 미룬다 — 현재는 조회·저장(북마크) 구조만 제공한다.
 */
@Entity
@Table(name = "blog_tip")
class BlogTip(
    @Column(name = "title", nullable = false, length = 500)
    var title: String,
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
    @Column(name = "category", length = 50)
    var category: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
