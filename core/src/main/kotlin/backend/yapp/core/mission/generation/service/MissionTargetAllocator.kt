package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode

object MissionTargetAllocator {
    fun allocate(
        baselineFrequency: Int,
        baselineAmountWon: Int,
        proposedAlternativeCount: Int,
    ): List<MissionTargetAllocation> {
        if (baselineFrequency !in 1..10 || baselineAmountWon !in 1..2_000_000 || proposedAlternativeCount !in 1..3) {
            throw BaseException(ErrorCode.MISSION_GENERATION_INPUT_INVALID)
        }
        val count = minOf(baselineFrequency, proposedAlternativeCount)
        val base = baselineFrequency / count
        val remainder = baselineFrequency % count
        val unitPriceWon = (baselineAmountWon / baselineFrequency / 100) * 100
        return List(count) { index ->
            val targetCount = base + if (index < remainder) 1 else 0
            MissionTargetAllocation(
                targetCount = targetCount,
                unitPriceWon = unitPriceWon,
                estimatedSavingsWon = targetCount * unitPriceWon,
            )
        }
    }
}

data class MissionTargetAllocation(
    val targetCount: Int,
    val unitPriceWon: Int,
    val estimatedSavingsWon: Int,
)

object MissionTitleRenderer {
    const val COUNT_PLACEHOLDER = "{count}"

    fun render(template: String, targetCount: Int): String {
        if (template.isBlank() || template.windowed(COUNT_PLACEHOLDER.length).count { it == COUNT_PLACEHOLDER } != 1) {
            throw IllegalArgumentException("Mission title template must contain exactly one count placeholder")
        }
        return template.replace(COUNT_PLACEHOLDER, targetCount.toString())
    }
}
