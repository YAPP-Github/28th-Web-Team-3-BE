package backend.yapp.core.mission.generation.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionRecommendationOfflineEvaluatorTest {
    @Test
    fun `calculates reproducible retrieval metrics and constraint violations`() {
        val result = MissionRecommendationOfflineEvaluator().evaluate(
            listOf(
                RecommendationEvaluationCase(listOf(1, 2, 3), setOf(1), 0),
                RecommendationEvaluationCase(listOf(4, 5, 6), setOf(5), 0),
            ),
            k = 2,
        )

        assertEquals(1.0, result.hitRateAtK)
        assertTrue(result.ndcgAtK in 0.8..1.0)
        assertEquals(0, result.constraintViolations)
    }
}
