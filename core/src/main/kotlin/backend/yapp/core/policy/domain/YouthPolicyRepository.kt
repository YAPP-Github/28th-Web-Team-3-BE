package backend.yapp.core.policy.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface YouthPolicyRepository : JpaRepository<YouthPolicy, Long> {
    fun findByExternalId(externalId: String): YouthPolicy?

    /**
     * 카테고리(4분류)·나이·지역 조건으로 정책을 검색한다. 각 조건이 null이면 해당 조건 미적용(전체).
     * - 나이: 정책의 대상 연령 범위가 사용자의 만 나이를 포함하면 대상(경계 미상=열림).
     * - 지역: 정책의 지역 목록(`,SEOUL,BUSAN,`)에 사용자의 거주지역이 포함되면 대상(전국 정책은 모든 지역 포함).
     *   지역 정보가 없는 정책(region_codes=null)은 지역 무관으로 보고 모두에게 노출한다.
     *
     * 정렬: 조회수(viewCount) 내림차순, 동률은 id 내림차순(최근 순).
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
          AND (
              :regionToken IS NULL
              OR p.regionCodes IS NULL
              OR p.regionCodes LIKE :regionToken
          )
        ORDER BY p.viewCount DESC, p.id DESC
        """,
    )
    fun search(
        @Param("category") category: String?,
        @Param("age") age: Int?,
        @Param("regionToken") regionToken: String?,
        pageable: Pageable,
    ): Page<YouthPolicy>
}
