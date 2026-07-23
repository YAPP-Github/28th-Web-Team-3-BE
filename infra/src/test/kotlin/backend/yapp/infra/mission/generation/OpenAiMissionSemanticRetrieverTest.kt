package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticDocument
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

class OpenAiMissionSemanticRetrieverTest {
    @Test
    fun `returns only the eight highest scoring documents`() {
        val client = OpenAiEmbeddingClient { inputs ->
            inputs.map { input ->
                if (input == "query") {
                    listOf(1.0, 0.0)
                } else {
                    listOf(1.0, input.substringAfter("document-").toDouble())
                }
            }
        }
        val retriever = OpenAiMissionSemanticRetriever(
            ObjectMapper(),
            EmbeddingProperties(apiKey = "test", dimensions = 2),
            client,
        )

        val result = retriever.retrieve(
            MissionSemanticRetrievalRequest(
                query = "query",
                candidates = (1L..10L).map { MissionSemanticDocument(it, "document-$it") },
            ),
        )

        assertEquals(8, result.scores.size)
        assertEquals((1L..8L).toSet(), result.scores.keys)
    }

    @Test
    fun `caches catalog embeddings and embeds only query after first request`() {
        val calls = AtomicInteger()
        val client = OpenAiEmbeddingClient { inputs ->
            calls.incrementAndGet()
            inputs.map { input ->
                if ("택시" in input) listOf(1.0, 0.0) else listOf(0.0, 1.0)
            }
        }
        val retriever = OpenAiMissionSemanticRetriever(
            ObjectMapper(),
            EmbeddingProperties(apiKey = "test", dimensions = 2),
            client,
        )
        val request = MissionSemanticRetrievalRequest(
            query = "택시 줄이기",
            candidates = listOf(
                MissionSemanticDocument(1, "택시를 대중교통으로 대체"),
                MissionSemanticDocument(2, "취미 구독 점검"),
            ),
        )

        val first = retriever.retrieve(request)
        val second = retriever.retrieve(request)
        retriever.retrieve(
            request.copy(
                candidates = request.candidates.map {
                    if (it.templateId == 2L) it.copy(text = "변경된 취미 구독 문서") else it
                },
            ),
        )

        assertEquals(5, calls.get())
        assertTrue(first.scores.getValue(1) > first.scores.getOrDefault(2, 0.0))
        assertEquals(first.scores, second.scores)
    }
}
