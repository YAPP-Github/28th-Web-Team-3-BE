package backend.yapp.core.mission.generation.port

import java.util.UUID

interface MissionRecommendationTracePort {
    fun recordRun(
        guestUserId: Long,
        algorithmVersion: String,
        semanticProvider: String,
        semanticModelVersion: String,
        eligibleTemplateIds: List<Long>,
        retrievedTemplateIds: Set<Long>,
        weeklyContextSnapshot: String,
        candidates: List<MissionRecommendationTraceCandidate>,
    )

    fun linkToJob(guestUserId: Long, jobId: UUID)

    fun markShown(jobId: UUID, templateIds: Set<Long>)
}

data class MissionRecommendationTraceCandidate(
    val candidate: MissionDraftCandidate,
    val rawScore: Double,
    val adjustedScore: Double,
    val retrieved: Boolean,
    val explorationApplied: Boolean,
    val appliedPenalties: String,
)
