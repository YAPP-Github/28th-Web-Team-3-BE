package backend.yapp.core.mission.generation.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissionTargetAllocatorTest {
    @Test
    fun `caps alternatives by baseline frequency so every mission gets at least one`() {
        val allocations = MissionTargetAllocator.allocate(
            baselineFrequency = 1,
            baselineAmountWon = 10_000,
            proposedAlternativeCount = 3,
        )

        assertEquals(listOf(1), allocations.map { it.targetCount })
        assertEquals(listOf(10_000), allocations.map { it.estimatedSavingsWon })
    }

    @Test
    fun `allocates remainder in AI order and floors unit price to one hundred won`() {
        val allocations = MissionTargetAllocator.allocate(
            baselineFrequency = 3,
            baselineAmountWon = 10_000,
            proposedAlternativeCount = 2,
        )

        assertEquals(listOf(2, 1), allocations.map { it.targetCount })
        assertEquals(listOf(3_300, 3_300), allocations.map { it.unitPriceWon })
        assertEquals(listOf(6_600, 3_300), allocations.map { it.estimatedSavingsWon })
    }

    @Test
    fun `rejects count placeholders used as non-frequency units`() {
        val invalidTemplates = listOf(
            "대량 구매로 {count}원 아끼기",
            "셀프 미용으로 {count}만 원 아끼기",
            "방문 주기 {count}달 늘리기",
            "도서관으로 {count}배 알차게 실천하기",
            "집에서 즐기는 {count}가지 레시피",
            "지인과 함께하는 {count}단계 프로젝트",
            "한 번에 {count}회분 준비하기",
            "이번 주 {count}번째 결제 미루기",
        )

        invalidTemplates.forEach { template ->
            assertFailsWith<IllegalArgumentException>(template) {
                MissionTitleRenderer.render(template, 2)
            }
        }
    }

    @Test
    fun `renders count placeholders with supported action frequency expressions`() {
        val validTemplates = mapOf(
            "집밥을 {count}회 실천하기" to "집밥을 2회 실천하기",
            "결제 전 {count} 번 확인하기" to "결제 전 2 번 확인하기",
            "대안을 {count}차례 확인하기" to "대안을 2차례 확인하기",
            "카페 방문을 {count}회로 제한하기" to "카페 방문을 2회로 제한하기",
            "배달 대신 {count}번의 포장 주문하기" to "배달 대신 2번의 포장 주문하기",
            "보유 물품을 {count}차례로 나눠 점검하기" to "보유 물품을 2차례로 나눠 점검하기",
            "무료 대안을 {count}회씩 활용하기" to "무료 대안을 2회씩 활용하기",
        )

        validTemplates.forEach { (template, expected) ->
            assertEquals(expected, MissionTitleRenderer.render(template, 2))
        }
    }

    @Test
    fun `accepts a 120 character template without truncation and rejects 121 characters`() {
        val maxLengthTemplate = "가".repeat(112) + "{count}회"
        val overLengthTemplate = "가".repeat(113) + "{count}회"

        assertEquals(120, maxLengthTemplate.length)
        assertEquals("가".repeat(112) + "2회", MissionTitleRenderer.render(maxLengthTemplate, 2))
        assertFailsWith<IllegalArgumentException> {
            MissionTitleRenderer.render(overLengthTemplate, 2)
        }
    }
}
