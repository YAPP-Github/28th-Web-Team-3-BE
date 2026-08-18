package backend.yapp.core.mission.generation.port

import backend.yapp.core.mission.generation.domain.MissionItem
import java.time.LocalDate
import java.util.UUID

interface MissionAlternativeGenerationPort {
    fun generate(request: MissionAlternativeGenerationRequest): MissionAlternativeGenerationResult
}

data class MissionAlternativeGenerationRequest(
    val item: MissionItem,
    val knowledgeContexts: List<MissionKnowledge>,
    val personalizationContext: String = "",
)

data class MissionAlternativeTemplate(
    val titleTemplate: String,
    val description: String,
)

data class MissionAlternativeGenerationResult(
    val alternatives: List<MissionAlternativeTemplate>,
    val source: MissionDraftGenerationSource,
)

interface MissionKnowledgeRetrievalPort {
    fun retrieve(request: MissionKnowledgeRetrievalRequest): MissionKnowledgeRetrievalResult
}

data class MissionKnowledgeRetrievalRequest(
    val item: MissionItem,
    val today: LocalDate,
)

data class MissionKnowledgeRetrievalResult(
    val knowledge: List<MissionKnowledge>,
    val candidateCount: Int,
)

enum class MissionKnowledgeSelectionPolicy {
    EMPTY,
    ALL,
    DETERMINISTIC_RANDOM_1,
}

data class MissionKnowledge(
    val id: Long,
    val content: String,
    val subjectKey: String?,
    val officialSourceUrl: String?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
)

interface MissionKnowledgeVerificationPort {
    /** Returns only knowledge that is safe to use after resolving contradictory subject groups. */
    fun verify(knowledge: List<MissionKnowledge>): List<MissionKnowledge>
}

interface MissionKnowledgeTracePort {
    fun record(trace: MissionKnowledgeTrace)
}

data class MissionKnowledgeTrace(
    val jobId: UUID,
    val item: MissionItem,
    val candidateCount: Int,
    val verifiedCount: Int,
    val selectedKnowledgeIds: List<Long>,
    val selectionPolicy: MissionKnowledgeSelectionPolicy,
)
