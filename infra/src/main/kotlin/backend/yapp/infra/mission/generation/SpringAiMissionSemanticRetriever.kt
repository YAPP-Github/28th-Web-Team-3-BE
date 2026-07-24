package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt
import org.springframework.ai.embedding.EmbeddingModel

class SpringAiMissionSemanticRetriever(
    private val client: MissionEmbeddingClient,
    private val provider: String,
    private val modelVersion: String,
) : MissionSemanticRetriever {
    private val templateCache = ConcurrentHashMap<String, Map<Long, FloatArray>>()

    override fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult {
        if (request.candidates.isEmpty()) {
            return MissionSemanticRetrievalResult(emptyMap(), provider, modelVersion)
        }
        val cacheKey = cacheKey(request)
        val templateVectors = templateCache.computeIfAbsent(cacheKey) {
            val embeddings = client.embed(request.candidates.map { document -> document.text })
            require(embeddings.size == request.candidates.size) {
                "Embedding count did not match mission catalog size"
            }
            request.candidates.map { it.templateId }.zip(embeddings).toMap()
        }
        val query = client.embed(listOf(request.query)).single()
        val scores = templateVectors.mapValues { (_, vector) -> cosine(query, vector) }
            .filterValues { it > 0.0 }
            .entries.sortedByDescending { it.value }
            .take(MAX_RESULTS)
            .associate { it.toPair() }
        return MissionSemanticRetrievalResult(
            scores = scores,
            provider = provider,
            modelVersion = modelVersion,
        )
    }

    private fun cacheKey(request: MissionSemanticRetrievalRequest): String {
        val content = request.candidates.sortedBy { it.templateId }
            .joinToString("|") { "${it.templateId}:${it.text}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$modelVersion:$hash"
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
    }
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
