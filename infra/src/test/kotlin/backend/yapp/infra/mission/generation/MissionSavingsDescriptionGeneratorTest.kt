package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionExpenseEstimate
import backend.yapp.core.mission.generation.port.MissionSavingsCopySource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import tools.jackson.databind.ObjectMapper

class MissionSavingsDescriptionGeneratorTest {
    private val prompt = MissionSavingsCopyPromptProperties(version = "test-v1", systemInstruction = "system", userInstruction = "user")

    @Test
    fun `AI copy preserves every policy amount including total savings`() {
        val generator = SpringAiMissionSavingsDescriptionGenerator(
            { MissionSavingsDescriptionAiResponse(listOf(MissionSavingsDescriptionAiCopy(1, "배달음식 기준 지출액은 13,000원이고 집밥은 8,000원이에요. 한 번 5,000원, 전체 10,000원 절약 예상이에요."))) },
            ObjectMapper(), prompt,
        )
        val result = generator.generate(listOf(candidate(estimatedSavingsWon = 10_000))).copies.single()
        assertEquals(MissionSavingsCopySource.AI, result.source)
        assertContains(checkNotNull(result.savingsDescription), "10,000원")
    }

    @Test
    fun `changed total savings falls back to deterministic policy copy`() {
        val generator = SpringAiMissionSavingsDescriptionGenerator(
            { MissionSavingsDescriptionAiResponse(listOf(MissionSavingsDescriptionAiCopy(1, "배달음식은 13,000원이고 집밥은 8,000원이에요. 한 번 5,000원, 전체 9,000원 절약 예상이에요."))) },
            ObjectMapper(), prompt,
        )
        val result = generator.generate(listOf(candidate(estimatedSavingsWon = 10_000))).copies.single()
        assertEquals(MissionSavingsCopySource.TEMPLATE_FALLBACK, result.source)
        assertContains(checkNotNull(result.savingsDescription), "10,000원")
    }

    private fun candidate(estimatedSavingsWon: Int) = MissionDraftCandidate(
        1, MissionCategory.MEAL, "제목", "설명", "REPLACE", MissionMetricType.COUNT, 2, "TIMES_PER_WEEK", estimatedSavingsWon,
        expenseEstimate = MissionExpenseEstimate("배달음식", "집밥", 13_000, 8_000, 5_000, estimatedSavingsWon, "ORDER", "POLICY_REFERENCE_V1", "POLICY_REFERENCE_V1"),
    )
}
