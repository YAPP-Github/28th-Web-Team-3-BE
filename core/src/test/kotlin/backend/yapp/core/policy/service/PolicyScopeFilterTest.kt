package backend.yapp.core.policy.service

import backend.yapp.core.policy.port.ExternalYouthPolicy
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyScopeFilterTest {
    private val today = LocalDate.of(2026, 8, 4)

    private fun policy(
        title: String = "청년 정책",
        medium: String? = null,
        applyPeriodText: String? = null,
        bizEndYmd: String? = null,
    ) = ExternalYouthPolicy(
        externalId = "P1",
        title = title,
        mediumCategory = medium,
        applyPeriodText = applyPeriodText,
        bizEndYmd = bizEndYmd,
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
    fun `resolveEndDate picks the latest date across formats`() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            PolicyScopeFilter.resolveEndDate(policy(applyPeriodText = "2026-01-01 ~ 2026-12-31")),
        )
    }
}
