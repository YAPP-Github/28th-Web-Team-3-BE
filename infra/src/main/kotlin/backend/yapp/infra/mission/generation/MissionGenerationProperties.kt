package backend.yapp.infra.mission.generation

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("mission.generation")
data class MissionGenerationProperties(
    val aiActivation: String = "off",
    val executor: ExecutorProperties = ExecutorProperties(),
    val staleRunningTimeout: Duration = Duration.ofMinutes(10),
    val prompt: MissionPromptProperties = MissionPromptProperties(),
    val recommendation: RecommendationProperties = RecommendationProperties(),
)

data class MissionPromptProperties(
    val version: String = "mission-copy-v1",
    val systemInstruction: String =
        "서버가 결정한 구조화 값은 변경하지 말고 title과 description만 자연스러운 한국어로 다듬으세요.",
    val userInstruction: String = "모든 후보를 정확히 한 번씩 반환하세요.",
)

data class RecommendationProperties(
    val provider: String = "personalized",
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
    val embedding: EmbeddingTraceProperties = EmbeddingTraceProperties(),
)

data class EmbeddingTraceProperties(
    val modelVersion: String = "text-embedding-3-small:256",
)

data class ExecutorProperties(
    val corePoolSize: Int = 2,
    val maxPoolSize: Int = 4,
    val queueCapacity: Int = 50,
)
