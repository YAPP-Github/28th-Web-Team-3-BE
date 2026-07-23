package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever

class KeywordMissionSemanticRetriever : MissionSemanticRetriever {
    override fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult {
        val queryTokens = request.query.tokens()
        val scores = request.candidates.associate { document ->
            val documentTokens = document.text.tokens()
            val union = queryTokens union documentTokens
            val score = if (union.isEmpty()) 0.0 else (queryTokens intersect documentTokens).size.toDouble() / union.size
            document.templateId to score
        }.filterValues { it > 0.0 }
            .entries.sortedByDescending { it.value }
            .take(MAX_RESULTS)
            .associate { it.toPair() }
        return MissionSemanticRetrievalResult(scores, "keyword-fallback", "token-jaccard-v1")
    }

    private fun String.tokens(): Set<String> =
        lowercase().split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length > 1 }.toSet()

    companion object {
        private const val MAX_RESULTS = 8
    }
}
