package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import java.util.PriorityQueue
import kotlin.math.sqrt
import org.springframework.ai.embedding.EmbeddingModel

class SpringAiMissionSemanticRetriever(
    private val client: MissionEmbeddingClient,
    private val provider: String,
    private val modelVersion: String,
) : MissionSemanticRetriever {
    override fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult {
        if (request.candidates.isEmpty()) {
            return MissionSemanticRetrievalResult(emptyMap(), provider, modelVersion)
        }
        val query = client.embed(listOf(request.query)).single()
        val topScores = PriorityQueue<ScoredDocument>(compareBy { it.score })
        request.candidates.chunked(EMBEDDING_BATCH_SIZE).forEach { candidates ->
            val embeddings = client.embed(candidates.map { it.text })
            require(embeddings.size == candidates.size) {
                "Embedding count did not match mission catalog batch size"
            }
            candidates.zip(embeddings).forEach { (candidate, vector) ->
                val score = cosine(query, vector)
                if (score > 0.0) {
                    topScores.add(ScoredDocument(candidate.templateId, score))
                    if (topScores.size > MAX_RESULTS) topScores.remove()
                }
            }
        }
        val scores = topScores.sortedByDescending { it.score }.associate { it.templateId to it.score }
        return MissionSemanticRetrievalResult(
            scores = scores,
            provider = provider,
            modelVersion = modelVersion,
        )
    }

    private fun cosine(left: FloatArray, right: FloatArray): Double {
        require(left.size == right.size)
        val dot = left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }
        val leftNorm = sqrt(left.sumOf { it.toDouble() * it.toDouble() })
        val rightNorm = sqrt(right.sumOf { it.toDouble() * it.toDouble() })
        return if (leftNorm == 0.0 || rightNorm == 0.0) 0.0 else dot / (leftNorm * rightNorm)
    }

    companion object {
        private const val MAX_RESULTS = 8
        private const val EMBEDDING_BATCH_SIZE = 16
    }

    private data class ScoredDocument(
        val templateId: Long,
        val score: Double,
    )
}

fun interface MissionEmbeddingClient {
    fun embed(inputs: List<String>): List<FloatArray>
}

class SpringAiMissionEmbeddingClient(
    private val embeddingModel: EmbeddingModel,
) : MissionEmbeddingClient {
    override fun embed(inputs: List<String>): List<FloatArray> = embeddingModel.embed(inputs)
}

class FallbackMissionSemanticRetriever(
    private val primary: MissionSemanticRetriever,
    private val fallback: MissionSemanticRetriever,
) : MissionSemanticRetriever {
    override fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult =
        runCatching { primary.retrieve(request) }
            .getOrElse { fallback.retrieve(request) }
}
