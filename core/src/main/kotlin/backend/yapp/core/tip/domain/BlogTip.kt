package backend.yapp.core.tip.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 절약 팁(블로그·영상 등에서 정리한 소비 절약 팁). `category`(식비/생활/취미) + `subcategory`(선택항목)로 분류하며
 * `sourceUrl`(원문 링크)을 함께 제공한다.
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
    @Column(name = "subcategory", length = 50)
    var subcategory: String? = null,
    @Column(name = "source_url", length = 1000)
    var sourceUrl: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
