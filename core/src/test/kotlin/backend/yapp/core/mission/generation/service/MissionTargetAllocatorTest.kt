package backend.yapp.core.mission.generation.service

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
