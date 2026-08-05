package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticDocument
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.springframework.ai.embedding.EmbeddingModel

class SpringAiMissionSemanticRetrieverTest {
    @Test
    fun `returns only the eight highest scoring documents`() {
        val client = MissionEmbeddingClient { inputs ->
            inputs.map { input ->
                if (input == "query") {
                    floatArrayOf(1.0f, 0.0f)
                } else {
                    floatArrayOf(1.0f, input.substringAfter("document-").toFloat())
                }
            }
        }
        val retriever = SpringAiMissionSemanticRetriever(client, "google-genai", "test-model:2")

        val result = retriever.retrieve(
            MissionSemanticRetrievalRequest(
                query = "query",
                candidates = (1L..10L).map { MissionSemanticDocument(it, "document-$it") },
            ),
        )

        assertEquals(8, result.scores.size)
        assertEquals((1L..8L).toSet(), result.scores.keys)
        assertEquals("google-genai", result.provider)
        assertEquals("test-model:2", result.modelVersion)
    }

    @Test
    fun `embeds catalog in bounded batches for every request`() {
        val calls = AtomicInteger()
        val batchSizes = mutableListOf<Int>()
        val client = MissionEmbeddingClient { inputs ->
            calls.incrementAndGet()
            batchSizes += inputs.size
            inputs.map { input ->
                if ("택시" in input) floatArrayOf(1.0f, 0.0f) else floatArrayOf(0.0f, 1.0f)
            }
        }
        val retriever = SpringAiMissionSemanticRetriever(client, "google-genai", "test-model:2")
        val request = MissionSemanticRetrievalRequest(
            query = "택시 줄이기",
            candidates = (1L..33L).map { id ->
                MissionSemanticDocument(id, if (id == 1L) "택시를 대중교통으로 대체" else "취미 구독 점검 $id")
            },
        )

        val first = retriever.retrieve(request)
        val second = retriever.retrieve(request)
        assertEquals(8, calls.get())
        assertEquals(listOf(1, 16, 16, 1, 1, 16, 16, 1), batchSizes)
        assertTrue(first.scores.getValue(1) > first.scores.getOrDefault(2, 0.0))
        assertEquals(first.scores, second.scores)
    }

    @Test
    fun `delegates embeddings to Spring AI model`() {
        val model = Mockito.mock(EmbeddingModel::class.java)
        val inputs = listOf("one", "two")
        val vectors = listOf(floatArrayOf(1.0f), floatArrayOf(2.0f))
        Mockito.`when`(model.embed(inputs)).thenReturn(vectors)

        val result = SpringAiMissionEmbeddingClient(model).embed(inputs)

        assertEquals(vectors, result)
        Mockito.verify(model).embed(inputs)
    }

    @Test
    fun `uses keyword retriever when Spring AI embedding fails`() {
        val fallbackCalls = AtomicInteger()
        val fallback = MissionSemanticRetriever {
            fallbackCalls.incrementAndGet()
            MissionSemanticRetrievalResult(mapOf(1L to 0.5), "keyword", "keyword-v1")
        }
        val retriever = FallbackMissionSemanticRetriever(
            primary = MissionSemanticRetriever { error("embedding unavailable") },
            fallback = fallback,
        )

        val result = retriever.retrieve(
            MissionSemanticRetrievalRequest(
                query = "택시",
                candidates = listOf(MissionSemanticDocument(1, "택시 줄이기")),
            ),
        )

        assertEquals(1, fallbackCalls.get())
        assertEquals("keyword", result.provider)
        assertEquals(mapOf(1L to 0.5), result.scores)
    }
}
