package backend.yapp.core.mission.generation.service

import kotlin.math.ln

class MissionRecommendationOfflineEvaluator {
    fun evaluate(cases: List<RecommendationEvaluationCase>, k: Int): RecommendationEvaluationResult {
        require(k > 0)
        if (cases.isEmpty()) return RecommendationEvaluationResult(0.0, 0.0, 0)
        val hitRate = cases.count { evaluation ->
            evaluation.rankedTemplateIds.take(k).any(evaluation.relevantTemplateIds::contains)
        }.toDouble() / cases.size
        val ndcg = cases.sumOf { evaluation ->
            val dcg = evaluation.rankedTemplateIds.take(k).mapIndexed { index, id ->
                if (id in evaluation.relevantTemplateIds) 1.0 / log2(index + 2.0) else 0.0
            }.sum()
            val ideal = (0 until minOf(k, evaluation.relevantTemplateIds.size))
                .sumOf { index -> 1.0 / log2(index + 2.0) }
            if (ideal == 0.0) 0.0 else dcg / ideal
        } / cases.size
        return RecommendationEvaluationResult(
            hitRateAtK = hitRate,
            ndcgAtK = ndcg,
            constraintViolations = cases.sumOf { it.constraintViolationCount },
        )
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)
}

data class RecommendationEvaluationCase(
    val rankedTemplateIds: List<Long>,
    val relevantTemplateIds: Set<Long>,
    val constraintViolationCount: Int = 0,
)

data class RecommendationEvaluationResult(
    val hitRateAtK: Double,
    val ndcgAtK: Double,
    val constraintViolations: Int,
)
