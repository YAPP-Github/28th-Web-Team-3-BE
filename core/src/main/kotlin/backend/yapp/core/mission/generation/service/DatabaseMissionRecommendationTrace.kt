package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTrace
import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTraceRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshot
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshotRepository
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionRecommendationTracePort
import backend.yapp.core.mission.generation.port.MissionRecommendationTraceCandidate
import java.time.Clock
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

open class DatabaseMissionRecommendationTrace(
    private val snapshotRepository: MissionRecommendationSnapshotRepository,
    private val candidateRepository: MissionRecommendationCandidateTraceRepository,
    private val clock: Clock,
) : MissionRecommendationTracePort {
    @Transactional
    open override fun recordRun(
        guestUserId: Long,
        algorithmVersion: String,
        semanticProvider: String,
        semanticModelVersion: String,
        eligibleTemplateIds: List<Long>,
        retrievedTemplateIds: Set<Long>,
        weeklyContextSnapshot: String,
        candidates: List<MissionRecommendationTraceCandidate>,
    ) {
        val snapshot = snapshotRepository.save(
            MissionRecommendationSnapshot(
                id = UUID.randomUUID(),
                guestUserId = guestUserId,
                algorithmVersion = algorithmVersion,
                semanticProvider = semanticProvider,
                semanticModelVersion = semanticModelVersion,
                eligibleCandidateIds = eligibleTemplateIds.joinToString(","),
                retrievedCandidateIds = retrievedTemplateIds.sorted().joinToString(","),
                weeklyContextSnapshot = weeklyContextSnapshot,
                createdAt = clock.instant(),
            ),
        )
        candidateRepository.saveAll(
            candidates.mapIndexed { index, trace ->
                MissionRecommendationCandidateTrace(
                    id = UUID.randomUUID(),
                    snapshotId = snapshot.id,
                    templateId = trace.candidate.templateId,
                    rankPosition = index + 1,
                    rawScore = trace.rawScore,
                    adjustedScore = trace.adjustedScore,
                    retrieved = trace.retrieved,
                    explorationApplied = trace.explorationApplied,
                    appliedPenalties = trace.appliedPenalties,
                )
            },
        )
    }

    @Transactional
    open override fun linkToJob(guestUserId: Long, jobId: UUID) {
        snapshotRepository.findFirstByGuestUserIdAndJobIdIsNullOrderByCreatedAtDesc(guestUserId)?.jobId = jobId
    }

    @Transactional
    open override fun markShown(jobId: UUID, templateIds: Set<Long>) {
        val snapshot = snapshotRepository.findByJobId(jobId) ?: return
        candidateRepository.findAllBySnapshotId(snapshot.id)
            .filter { it.templateId in templateIds }
            .forEach { it.shown = true }
    }
}
