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
    const val MAX_TEMPLATE_LENGTH = 120

    private val actionCountPattern = Regex(
        """\{count}\s*(?:회|번|차례)(?:의|로|으로|만|씩|을|를|은|는|도|에|까지|부터|정도|내외|이상|이하)?(?=\s|$|[,.!?·:;)\]])""",
    )

    fun validate(template: String) {
        require(template.isNotBlank()) { "Mission title template must not be blank" }
        require(template.length <= MAX_TEMPLATE_LENGTH) {
            "Mission title template must not exceed $MAX_TEMPLATE_LENGTH characters"
        }
        require(template.windowed(COUNT_PLACEHOLDER.length).count { it == COUNT_PLACEHOLDER } == 1) {
            "Mission title template must contain exactly one count placeholder"
        }
        require(actionCountPattern.containsMatchIn(template)) {
            "Mission title count placeholder must represent an action frequency"
        }
    }

    fun render(template: String, targetCount: Int): String {
        validate(template)
        return template.replace(COUNT_PLACEHOLDER, targetCount.toString())
    }
}
