package backend.yapp.core.mission.generation.port

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import java.util.UUID

interface MissionDraftCandidateProvider {
    fun candidates(guestUserId: Long, categories: Set<MissionCategory>): List<MissionDraftCandidate>
}

interface MissionDraftContentGenerator {
    fun generate(request: MissionDraftContentRequest): MissionDraftContentResult
}

data class MissionDraftCandidate(
    val templateId: Long,
    val category: MissionCategory,
    val templateTitle: String,
    val templateDescription: String,
    val actionCode: String,
    val metricType: MissionMetricType,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String = "V1",
)

data class MissionDraftContentRequest(
    val jobId: UUID,
    val guestUserId: Long,
    val candidates: List<MissionDraftCandidate>,
)

data class MissionDraftCopy(
    val templateId: Long,
    val title: String,
    val description: String,
)

data class MissionDraftContentResult(
    val copies: List<MissionDraftCopy>,
    val source: MissionDraftGenerationSource,
)

enum class MissionDraftGenerationSource {
    MOCK,
    DIRECT,
    AI,
    @Deprecated("Legacy source retained for persisted OpenAI generation history")
    OPENAI,
    TEMPLATE_FALLBACK,
}
