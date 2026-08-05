package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraft
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftCandidateProvider
import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionRecommendationTracePort
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionGenerationExecutor(
    private val workService: MissionGenerationWorkService,
    private val contentGenerator: MissionDraftContentGenerator,
) {
    fun execute(jobId: UUID): MissionGenerationExecutionResult {
        val work = when (val preparation = workService.prepare(jobId)) {
            is MissionGenerationPreparation.Claimed -> preparation.work
            MissionGenerationPreparation.ActiveLease -> return MissionGenerationExecutionResult.ACTIVE_LEASE
            MissionGenerationPreparation.Skipped -> return MissionGenerationExecutionResult.SKIPPED
        }
        if (work.candidates.isEmpty()) {
            workService.complete(
                work = work,
                copiesByTemplateId = emptyMap(),
                generationSource = backend.yapp.core.mission.generation.port.MissionDraftGenerationSource.TEMPLATE_FALLBACK,
            )
            return MissionGenerationExecutionResult.COMPLETED
        }
        val result = contentGenerator.generate(
            MissionDraftContentRequest(
                jobId = work.jobId,
                guestUserId = work.guestUserId,
                candidates = work.candidates,
            ),
        )
        workService.complete(
            work = work,
            copiesByTemplateId = result.copies.associateBy { it.templateId },
            generationSource = result.source,
        )
        return MissionGenerationExecutionResult.COMPLETED
    }
}

@Service
class MissionGenerationWorkService(
    private val jobRepository: MissionGenerationJobRepository,
    private val surveyRepository: MissionSurveyRepository,
    private val candidateProvider: MissionDraftCandidateProvider,
    private val draftRepository: MissionDraftRepository,
    private val tracePort: MissionRecommendationTracePort,
    private val clock: Clock,
) {
    @Transactional
    fun prepare(jobId: UUID): MissionGenerationPreparation {
        val job = jobRepository.findByIdForUpdate(jobId) ?: return MissionGenerationPreparation.Skipped
        val now = clock.instant()
        if (job.status == backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus.RUNNING &&
            job.leaseExpiresAt?.isAfter(now) == true
        ) {
            return MissionGenerationPreparation.ActiveLease
        }
        val leaseToken = UUID.randomUUID()
        if (!job.claim(now, leaseToken, DEFAULT_LEASE_DURATION)) return MissionGenerationPreparation.Skipped

        val survey = surveyRepository.findByGuestUserId(job.guestUserId)
            ?: error("Mission survey disappeared before generation")
        val categories = survey.answerRows()
            .map { MissionCategory.valueOf(it.categoryCode) }
            .toSet()
        val candidates = candidateProvider.candidates(job.guestUserId, categories)
            .groupBy { it.category }
            .flatMap { (_, categoryCandidates) -> categoryCandidates.take(MAX_DRAFTS_PER_CATEGORY) }
        tracePort.linkToJob(job.guestUserId, job.id)

        return MissionGenerationPreparation.Claimed(
            MissionGenerationWork(job.id, job.guestUserId, candidates, leaseToken),
        )
    }

    @Transactional
    fun complete(
        work: MissionGenerationWork,
        copiesByTemplateId: Map<Long, backend.yapp.core.mission.generation.port.MissionDraftCopy>,
        generationSource: backend.yapp.core.mission.generation.port.MissionDraftGenerationSource,
    ) {
        val job = jobRepository.findByIdForUpdate(work.jobId) ?: return
        if (!job.ownsLease(work.leaseToken, clock.instant())) return

        val now = clock.instant()
        val drafts = work.candidates.map { candidate ->
            val copy = copiesByTemplateId[candidate.templateId]
            MissionDraft(
                id = UUID.randomUUID(),
                jobId = work.jobId,
                templateId = candidate.templateId,
                category = candidate.category,
                title = copy?.title?.takeIf(String::isNotBlank)?.take(MAX_TITLE_LENGTH)
                    ?: candidate.templateTitle,
                description = copy?.description?.takeIf(String::isNotBlank)?.take(MAX_DESCRIPTION_LENGTH)
                    ?: candidate.templateDescription,
                actionCode = candidate.actionCode,
                metricType = candidate.metricType,
                targetCount = candidate.targetCount,
                targetUnit = candidate.targetUnit,
                estimatedSavingsWon = candidate.estimatedSavingsWon,
                savingsEstimateVersion = candidate.savingsEstimateVersion,
                createdAt = now,
            )
        }
        draftRepository.saveAll(drafts)
        job.succeed(now, now.plus(DRAFT_TTL), generationSource)
        tracePort.markShown(job.id, drafts.map { it.templateId }.toSet())
    }

    companion object {
        private val DEFAULT_LEASE_DURATION: Duration = Duration.ofMinutes(10)
        private const val MAX_DRAFTS_PER_CATEGORY = 4
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_DESCRIPTION_LENGTH = 500
        private val DRAFT_TTL: Duration = Duration.ofHours(24)
    }
}

data class MissionGenerationWork(
    val jobId: UUID,
    val guestUserId: Long,
    val candidates: List<MissionDraftCandidate>,
    val leaseToken: UUID = UUID.randomUUID(),
)

sealed interface MissionGenerationPreparation {
    data class Claimed(val work: MissionGenerationWork) : MissionGenerationPreparation
    data object ActiveLease : MissionGenerationPreparation
    data object Skipped : MissionGenerationPreparation
}

enum class MissionGenerationExecutionResult {
    COMPLETED,
    ACTIVE_LEASE,
    SKIPPED,
}
