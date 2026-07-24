package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt
import tools.jackson.databind.ObjectMapper

class OpenAiMissionSemanticRetriever(
    private val objectMapper: ObjectMapper,
    private val properties: EmbeddingProperties,
    private val client: OpenAiEmbeddingClient = JdkOpenAiEmbeddingClient(objectMapper, properties),
) : MissionSemanticRetriever {
    private val templateCache = ConcurrentHashMap<String, Map<Long, List<Double>>>()

    override fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult {
        if (request.candidates.isEmpty()) {
            return MissionSemanticRetrievalResult(emptyMap(), "openai", properties.model)
        }
        val cacheKey = cacheKey(request)
        val templateVectors = templateCache.computeIfAbsent(cacheKey) {
            val embeddings = client.embed(request.candidates.map { document -> document.text })
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
            provider = "openai",
            modelVersion = "${properties.model}:${properties.dimensions}",
        )
    }

    private fun cacheKey(request: MissionSemanticRetrievalRequest): String {
        val content = request.candidates.sortedBy { it.templateId }
            .joinToString("|") { "${it.templateId}:${it.text}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "${properties.model}:${properties.dimensions}:$hash"
    }

    private fun cosine(left: List<Double>, right: List<Double>): Double {
        require(left.size == right.size)
        val dot = left.indices.sumOf { left[it] * right[it] }
        val leftNorm = sqrt(left.sumOf { it * it })
        val rightNorm = sqrt(right.sumOf { it * it })
        return if (leftNorm == 0.0 || rightNorm == 0.0) 0.0 else dot / (leftNorm * rightNorm)
    }

    companion object {
        private const val MAX_RESULTS = 8
    }
}

fun interface OpenAiEmbeddingClient {
    fun embed(inputs: List<String>): List<List<Double>>
}

class JdkOpenAiEmbeddingClient(
    private val objectMapper: ObjectMapper,
    private val properties: EmbeddingProperties,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout)
        .build(),
) : OpenAiEmbeddingClient {
    override fun embed(inputs: List<String>): List<List<Double>> {
        require(properties.apiKey.isNotBlank()) { "OPENAI_API_KEY is required for embeddings" }
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to properties.model,
                "input" to inputs,
                "encoding_format" to "float",
                "dimensions" to properties.dimensions,
            ),
        )
        val response = httpClient.send(
            HttpRequest.newBuilder(embeddingsUri())
                .timeout(properties.requestTimeout)
                .header("Authorization", "Bearer ${properties.apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() in 200..299) {
            "OpenAI Embeddings API returned HTTP ${response.statusCode()}"
        }
        return objectMapper.readTree(response.body()).path("data").toList()
            .sortedBy { it.path("index").asInt() }
            .map { item -> item.path("embedding").toList().map { value -> value.asDouble() } }
    }

    private fun embeddingsUri(): URI =
        properties.baseUrl.toString().trimEnd('/').let { URI.create("$it/v1/embeddings") }
}
