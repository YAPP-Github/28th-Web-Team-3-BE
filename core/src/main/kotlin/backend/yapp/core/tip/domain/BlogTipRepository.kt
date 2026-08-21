package backend.yapp.core.tip.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BlogTipRepository : JpaRepository<BlogTip, Long> {
    /** 카테고리(식비/생활/취미)·선택항목(subcategory) 선택 필터. 둘 다 null이면 전체. 등록 순(id) 정렬. */
    @Query(
        """
        select tip from BlogTip tip
        where (:category is null or tip.category = :category)
          and (:subcategory is null or tip.subcategory = :subcategory)
        order by tip.id asc
        """,
    )
    fun search(
        @Param("category") category: String?,
        @Param("subcategory") subcategory: String?,
        pageable: Pageable,
    ): Page<BlogTip>
}
