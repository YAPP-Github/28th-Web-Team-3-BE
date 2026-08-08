package backend.yapp.core.tip.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BlogTipRepository : JpaRepository<BlogTip, Long> {
    fun findByCategory(category: String, pageable: Pageable): Page<BlogTip>
}
