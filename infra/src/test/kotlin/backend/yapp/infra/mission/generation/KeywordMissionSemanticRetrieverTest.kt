package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticDocument
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class KeywordMissionSemanticRetrieverTest {
    @Test
    fun `returns at most eight matching documents`() {
        val result = KeywordMissionSemanticRetriever().retrieve(
            MissionSemanticRetrievalRequest(
                query = "공통 절약",
                candidates = (1L..10L).map {
                    MissionSemanticDocument(it, "공통 절약 미션 $it")
                },
            ),
        )

        assertEquals(8, result.scores.size)
        assertEquals((1L..8L).toSet(), result.scores.keys)
    }
}
