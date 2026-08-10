package backend.yapp.core.policy.service

import backend.yapp.core.policy.domain.PolicyCategory
import backend.yapp.core.policy.port.ExternalYouthPolicy
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyScopeFilterTest {
    private val today = LocalDate.of(2026, 8, 4)

    private fun policy(
        title: String = "청년 정책",
        medium: String? = null,
        applyPeriodText: String? = null,
        bizEndYmd: String? = null,
        regionCode: String? = null,
    ) = ExternalYouthPolicy(
        externalId = "P1",
        title = title,
        mediumCategory = medium,
        applyPeriodText = applyPeriodText,
        bizEndYmd = bizEndYmd,
        regionCode = regionCode,
    )

    @Test
    fun `included medium category is in scope`() {
        assertTrue(PolicyScopeFilter.isInScope(policy(medium = "주택 및 거주지"), today))
    }

    @Test
    fun `excluded medium category is out of scope`() {
        assertFalse(PolicyScopeFilter.isInScope(policy(medium = "창업"), today))
    }

    @Test
    fun `employment category is included only when title contains 자격증`() {
        assertTrue(PolicyScopeFilter.isInScope(policy(title = "청년 자격증 응시료 지원", medium = "취업"), today))
        assertFalse(PolicyScopeFilter.isInScope(policy(title = "청년 면접 정장 대여", medium = "취업"), today))
    }

    @Test
    fun `any matching category among multiple values includes the policy`() {
        assertTrue(PolicyScopeFilter.isInScope(policy(medium = "창업,건강"), today))
    }

    @Test
    fun `expired policy is excluded`() {
        assertFalse(PolicyScopeFilter.isInScope(policy(medium = "건강", applyPeriodText = "20260101 ~ 20260731"), today))
    }

    @Test
    fun `policy with unknown period is included`() {
        assertTrue(PolicyScopeFilter.isInScope(policy(medium = "건강", applyPeriodText = null, bizEndYmd = null), today))
    }

    @Test
    fun `policy with future deadline is included`() {
        assertTrue(PolicyScopeFilter.isInScope(policy(medium = "건강", applyPeriodText = "20260803 ~ 20261231"), today))
    }

    @Test
    fun `resolveCategory maps mediums to the 4 display categories`() {
        assertEquals(PolicyCategory.HOUSING, PolicyScopeFilter.resolveCategory(policy(medium = "전월세 및 주거급여 지원")))
        assertEquals(PolicyCategory.FINANCE, PolicyScopeFilter.resolveCategory(policy(medium = "취약계층 및 금융지원")))
        assertEquals(PolicyCategory.FINANCE, PolicyScopeFilter.resolveCategory(policy(medium = "재직자")))
        assertEquals(PolicyCategory.EDUCATION, PolicyScopeFilter.resolveCategory(policy(medium = "교육비지원")))
        assertEquals(PolicyCategory.WELFARE, PolicyScopeFilter.resolveCategory(policy(medium = "건강")))
    }

    @Test
    fun `resolveCategory maps 취업 자격증 to EDUCATION only with 자격증 in title`() {
        assertEquals(
            PolicyCategory.EDUCATION,
            PolicyScopeFilter.resolveCategory(policy(title = "청년 자격증 응시료 지원", medium = "취업")),
        )
        assertNull(PolicyScopeFilter.resolveCategory(policy(title = "청년 면접비 지원", medium = "취업")))
    }

    @Test
    fun `resolveCategory picks the highest-priority category among multiple`() {
        // 주거 > 금융 > 교육 > 복지
        assertEquals(
            PolicyCategory.HOUSING,
            PolicyScopeFilter.resolveCategory(policy(medium = "취약계층 및 금융지원,주택 및 거주지")),
        )
        assertEquals(
            PolicyCategory.FINANCE,
            PolicyScopeFilter.resolveCategory(policy(medium = "건강,취약계층 및 금융지원")),
        )
    }

    @Test
    fun `resolveCategory returns null for out-of-scope medium`() {
        assertNull(PolicyScopeFilter.resolveCategory(policy(medium = "창업")))
    }

    @Test
    fun `resolveRegionCodes normalizes zipCd sido prefixes to residential areas`() {
        // 11=서울, 26=부산 → 정렬된 구분자 문자열
        assertEquals(",SEOUL,BUSAN,", PolicyScopeFilter.resolveRegionCodes(policy(regionCode = "26110,11110,11140")))
        // 12=전남광주통합특별시 → JEONNAM
        assertEquals(",JEONNAM,", PolicyScopeFilter.resolveRegionCodes(policy(regionCode = "12110,12130")))
        // 지역코드 없음 → null
        assertNull(PolicyScopeFilter.resolveRegionCodes(policy(regionCode = null)))
    }

    @Test
    fun `resolveEndDate picks the latest date across formats`() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            PolicyScopeFilter.resolveEndDate(policy(applyPeriodText = "2026-01-01 ~ 2026-12-31")),
        )
    }
}
