package backend.yapp.infra.mission.generation

import java.net.URI
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("mission.generation")
data class MissionGenerationProperties(
    val provider: String = "mock",
    val executor: ExecutorProperties = ExecutorProperties(),
    val staleRunningTimeout: Duration = Duration.ofMinutes(10),
    val openai: OpenAiProperties = OpenAiProperties(),
    val recommendation: RecommendationProperties = RecommendationProperties(),
)

data class RecommendationProperties(
    val provider: String = "personalized",
    val semanticProvider: String = "keyword",
    val algorithmVersion: String = "rule-v1",
    val normalReduction: Int = 1,
    val aggressiveReduction: Int = 2,
    val normalReplacementCount: Int = 1,
    val aggressiveReplacementCount: Int = 2,
    val exactCooldownDays: Long = 56,
    val familyCooldownDays: Long = 28,
    val signalDecayDays: Long = 84,
    val categoryConcentrationPenalty: Double = 0.04,
    val archetypeConcentrationPenalty: Double = 0.08,
    val recentCategoryExposurePenalty: Double = 0.03,
    val explorationBonus: Double = 0.05,
    val explorationRate: Double = 0.20,
    val embedding: EmbeddingProperties = EmbeddingProperties(),
)

data class EmbeddingProperties(
    val baseUrl: URI = URI.create("https://api.openai.com"),
    val apiKey: String = "",
    val model: String = "text-embedding-3-small",
    val dimensions: Int = 256,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val requestTimeout: Duration = Duration.ofSeconds(10),
)

data class ExecutorProperties(
    val corePoolSize: Int = 2,
    val maxPoolSize: Int = 4,
    val queueCapacity: Int = 50,
)

data class OpenAiProperties(
    val baseUrl: URI = URI.create("https://api.openai.com"),
    val apiKey: String = "",
    val safetySalt: String = "",
    val model: String = "gpt-5.6-terra",
    val reasoningEffort: String = "low",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val requestTimeout: Duration = Duration.ofSeconds(20),
    val maxOutputTokens: Int = 2_000,
)
