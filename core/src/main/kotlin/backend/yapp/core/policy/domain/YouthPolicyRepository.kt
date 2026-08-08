package backend.yapp.core.policy.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface YouthPolicyRepository : JpaRepository<YouthPolicy, Long> {
    fun findByExternalId(externalId: String): YouthPolicy?

    /**
     * 카테고리(4분류)·나이 조건으로 정책을 검색한다. 두 조건 모두 null이면 미적용(전체).
     * 나이 조건: 정책의 대상 연령 범위가 사용자의 만 나이를 포함하면 대상(경계 미상=열림).
     */
    @Query(
        """
        SELECT p FROM YouthPolicy p
        WHERE (:category IS NULL OR p.category = :category)
          AND (
              :age IS NULL
              OR (
                  (p.targetMinAge IS NULL OR p.targetMinAge <= :age)
                  AND (p.targetMaxAge IS NULL OR p.targetMaxAge >= :age)
              )
          )
        """,
    )
    fun search(
        @Param("category") category: String?,
        @Param("age") age: Int?,
        pageable: Pageable,
    ): Page<YouthPolicy>
}
