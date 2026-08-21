package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionDraft
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionGenerationOutboxRepository
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionKnowledgeTrace
import backend.yapp.core.mission.generation.port.MissionKnowledgeTracePort
import backend.yapp.core.mission.generation.port.MissionKnowledgeVerificationPort
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory

@Service
class MissionGenerationExecutor(
    private val workService: MissionGenerationWorkService,
    private val knowledgeRetrievalPort: MissionKnowledgeRetrievalPort,
    private val knowledgeVerificationPort: MissionKnowledgeVerificationPort,
    private val knowledgeTracePort: MissionKnowledgeTracePort,
    private val alternativeGenerator: MissionAlternativeGenerationPort,
    private val outboxRepository: MissionGenerationOutboxRepository? = null,
    private val latencyRecorder: MissionGenerationLatencyRecorder = NoopMissionGenerationLatencyRecorder,
) {
    fun execute(jobId: UUID, generation: Int): MissionGenerationExecutionResult {
        val workerReceivedAt = workService.clock.instant()
        recordQueueDelay(jobId, generation, workerReceivedAt)
        val work = when (val preparation = workService.prepare(jobId)) {
            is MissionGenerationPreparation.Claimed -> preparation.work
            MissionGenerationPreparation.ActiveLease -> {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.WORKER_TOTAL,
                    MissionGenerationLatencyOutcome.DUPLICATE,
                    null,
                    Duration.between(workerReceivedAt, workService.clock.instant()),
                    jobId,
                )
                return MissionGenerationExecutionResult.ACTIVE_LEASE
            }
            MissionGenerationPreparation.Skipped -> {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.WORKER_TOTAL,
                    MissionGenerationLatencyOutcome.SKIPPED,
                    null,
                    Duration.between(workerReceivedAt, workService.clock.instant()),
                    jobId,
                )
                return MissionGenerationExecutionResult.SKIPPED
            }
        }
        try {
            val personalizationContext = MissionSearchQueryFactory.create(
                item = work.item,
                birthDate = work.birthDate,
                address = work.address,
                today = LocalDate.now(workService.clock),
                baselineFrequency = work.baselineFrequency,
                baselineAmountWon = work.baselineAmountWon,
            )
            val retrievalStartedAt = workService.clock.instant()
            val retrieved = runCatching {
                knowledgeRetrievalPort.retrieve(
                    MissionKnowledgeRetrievalRequest(
                        item = work.item,
                        today = LocalDate.now(workService.clock),
                    ),
                )
            }.onFailure {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.RETRIEVAL,
                    MissionGenerationLatencyOutcome.FAILED,
                    null,
                    Duration.between(retrievalStartedAt, workService.clock.instant()),
                    work.jobId,
                )
            }.getOrNull()
            if (retrieved != null) {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.RETRIEVAL,
                    MissionGenerationLatencyOutcome.SUCCEEDED,
                    null,
                    Duration.between(retrievalStartedAt, workService.clock.instant()),
                    work.jobId,
                )
            }
            val verificationStartedAt = workService.clock.instant()
            val verification = runCatching {
                knowledgeVerificationPort.verify(retrieved?.knowledge.orEmpty())
                    .mapTo(mutableSetOf()) { it.id }
            }.onFailure {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.VERIFICATION,
                    MissionGenerationLatencyOutcome.FAILED,
                    null,
                    Duration.between(verificationStartedAt, workService.clock.instant()),
                    work.jobId,
                )
            }
            val verifiedIds = verification.getOrDefault(emptySet())
            if (verification.isSuccess) {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.VERIFICATION,
                    MissionGenerationLatencyOutcome.SUCCEEDED,
                    null,
                    Duration.between(verificationStartedAt, workService.clock.instant()),
                    work.jobId,
                )
            }
            val verifiedKnowledge = retrieved?.knowledge.orEmpty().filter { it.id in verifiedIds }
            val selection = MissionKnowledgeSelector.select(work.jobId, verifiedKnowledge)
            runCatching {
                knowledgeTracePort.record(
                    MissionKnowledgeTrace(
                        jobId = work.jobId,
                        item = work.item,
                        candidateCount = retrieved?.candidateCount ?: 0,
                        verifiedCount = verifiedKnowledge.size,
                        selectedKnowledgeIds = selection.knowledge.map { it.id },
                        selectionPolicy = selection.policy,
                    ),
                )
            }.onFailure {
                log.warn("mission_generation.knowledge_trace.failed")
            }
            log.info(
                "mission_generation.knowledge.used item={} candidateCount={} verifiedCount={} selectedCount={} policy={}",
                work.item,
                retrieved?.candidateCount ?: 0,
                verifiedKnowledge.size,
                selection.knowledge.size,
                selection.policy,
            )
            val generationStartedAt = workService.clock.instant()
            val generated = try {
                alternativeGenerator.generate(
                    MissionAlternativeGenerationRequest(
                        jobId = work.jobId,
                        item = work.item,
                        knowledgeContexts = selection.knowledge,
                        personalizationContext = personalizationContext,
                    ),
                )
            } catch (exception: Exception) {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.AI_GENERATION,
                    MissionGenerationLatencyOutcome.FAILED,
                    null,
                    Duration.between(generationStartedAt, workService.clock.instant()),
                    work.jobId,
                )
                throw exception
            }
            latencyRecorder.record(
                MissionGenerationLatencyStage.AI_GENERATION,
                MissionGenerationLatencyOutcome.SUCCEEDED,
                generated.source,
                Duration.between(generationStartedAt, workService.clock.instant()),
                work.jobId,
            )
            val persistenceStartedAt = workService.clock.instant()
            val completion = try {
                workService.complete(work, generated)
            } catch (exception: Exception) {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.PERSISTENCE,
                    MissionGenerationLatencyOutcome.FAILED,
                    generated.source,
                    Duration.between(persistenceStartedAt, workService.clock.instant()),
                    work.jobId,
                )
                throw exception
            }
            latencyRecorder.record(
                MissionGenerationLatencyStage.PERSISTENCE,
                if (completion == null) MissionGenerationLatencyOutcome.SKIPPED else MissionGenerationLatencyOutcome.SUCCEEDED,
                generated.source,
                Duration.between(persistenceStartedAt, workService.clock.instant()),
                work.jobId,
            )
            latencyRecorder.record(
                MissionGenerationLatencyStage.WORKER_TOTAL,
                if (completion == null) MissionGenerationLatencyOutcome.SKIPPED else MissionGenerationLatencyOutcome.SUCCEEDED,
                generated.source,
                Duration.between(workerReceivedAt, workService.clock.instant()),
                work.jobId,
            )
            completion?.let {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.END_TO_END,
                    MissionGenerationLatencyOutcome.SUCCEEDED,
                    it.source,
                    Duration.between(it.createdAt, it.completedAt),
                    work.jobId,
                )
            }
            return MissionGenerationExecutionResult.COMPLETED
        } catch (exception: Exception) {
            val release = workService.releaseOrFail(work)
            latencyRecorder.record(
                MissionGenerationLatencyStage.WORKER_TOTAL,
                release.outcome,
                null,
                Duration.between(workerReceivedAt, workService.clock.instant()),
                work.jobId,
            )
            release.completion?.let {
                latencyRecorder.record(
                    MissionGenerationLatencyStage.END_TO_END,
                    MissionGenerationLatencyOutcome.FAILED,
                    null,
                    Duration.between(it.createdAt, it.completedAt),
                    work.jobId,
                )
            }
            throw exception
        }
    }

    private fun recordQueueDelay(jobId: UUID, generation: Int, receivedAt: java.time.Instant) {
        val publishedAt = outboxRepository?.findByJobIdAndGeneration(jobId, generation)?.publishedAt
        latencyRecorder.record(
            MissionGenerationLatencyStage.QUEUE,
            if (publishedAt == null) MissionGenerationLatencyOutcome.UNPAIRED else MissionGenerationLatencyOutcome.SUCCEEDED,
            null,
            publishedAt?.let { Duration.between(it, receivedAt) } ?: Duration.ZERO,
            jobId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MissionGenerationExecutor::class.java)
    }
}

@Service
class MissionGenerationWorkService(
    private val jobRepository: MissionGenerationJobRepository,
    private val profileRepository: OnboardingProfileRepository,
    private val templateRepository: MissionDraftTemplateRepository,
    private val draftRepository: MissionDraftRepository,
    val clock: Clock,
) {
    @Transactional
    fun prepare(jobId: UUID): MissionGenerationPreparation {
        val job = jobRepository.findByIdForUpdate(jobId) ?: return MissionGenerationPreparation.Skipped
        val now = clock.instant()
        if (job.status == MissionGenerationJobStatus.RUNNING && job.leaseExpiresAt?.isAfter(now) == true) {
            return MissionGenerationPreparation.ActiveLease
        }
        val leaseToken = UUID.randomUUID()
        if (!job.claim(now, leaseToken, DEFAULT_LEASE_DURATION)) return MissionGenerationPreparation.Skipped

        val item = checkNotNull(job.item) { "Mission generation item is missing" }
        val profile = checkNotNull(profileRepository.findByGuestUserId(job.guestUserId)) {
            "Onboarding profile disappeared before generation"
        }
        return MissionGenerationPreparation.Claimed(
            MissionGenerationWork(
                jobId = job.id,
                guestUserId = job.guestUserId,
                item = item,
                baselineFrequency = checkNotNull(job.baselineFrequency),
                baselineAmountWon = checkNotNull(job.baselineAmountWon),
                birthDate = checkNotNull(profile.birthDate),
                address = profile.address,
                leaseToken = leaseToken,
            ),
        )
    }

    @Transactional
    fun complete(
        work: MissionGenerationWork,
        generated: MissionAlternativeGenerationResult,
    ): MissionGenerationCompletion? {
        val job = jobRepository.findByIdForUpdate(work.jobId) ?: return null
        if (!job.ownsLease(work.leaseToken, clock.instant())) return null
        if (generated.alternatives.isEmpty() || generated.alternatives.size > MAX_ALTERNATIVES) {
            error("AI returned an invalid alternative count")
        }

        val selectedAlternatives = generated.alternatives.take(work.baselineFrequency)
        val allocations = MissionTargetAllocator.allocate(
            work.baselineFrequency,
            work.baselineAmountWon,
            selectedAlternatives.size,
        )
        val template = checkNotNull(templateRepository.findByTargetCodeAndActiveTrue(work.item.name)) {
            "Mission item template is missing: ${work.item.name}"
        }
        val now = clock.instant()
        val drafts = selectedAlternatives.zip(allocations).mapIndexed { index, (alternative, allocation) ->
            MissionDraft(
                id = UUID.randomUUID(),
                jobId = work.jobId,
                templateId = template.id,
                category = work.item.category,
                item = work.item,
                titleTemplate = alternative.titleTemplate,
                priorityOrder = index + 1,
                title = MissionTitleRenderer.render(alternative.titleTemplate, allocation.targetCount),
                description = alternative.description.take(MAX_DESCRIPTION_LENGTH),
                actionCode = work.item.name,
                metricType = MissionMetricType.COUNT,
                targetCount = allocation.targetCount,
                targetUnit = "TIMES_PER_WEEK",
                estimatedSavingsWon = allocation.estimatedSavingsWon,
                savingsEstimateVersion = "V2_DETERMINISTIC",
                createdAt = now,
            )
        }
        draftRepository.saveAll(drafts)
        job.succeed(now, now.plus(DRAFT_TTL), generated.source)
        return MissionGenerationCompletion(job.createdAt, now, generated.source)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun releaseOrFail(work: MissionGenerationWork): MissionGenerationReleaseResult {
        val job = jobRepository.findByIdForUpdate(work.jobId)
            ?: return MissionGenerationReleaseResult(MissionGenerationLatencyOutcome.SKIPPED)
        if (!job.releaseOrFail(work.leaseToken, clock.instant(), MAX_ATTEMPTS)) {
            return MissionGenerationReleaseResult(MissionGenerationLatencyOutcome.SKIPPED)
        }
        return if (job.status == MissionGenerationJobStatus.FAILED) {
            MissionGenerationReleaseResult(
                MissionGenerationLatencyOutcome.FAILED,
                MissionGenerationCompletion(job.createdAt, checkNotNull(job.completedAt), null),
            )
        } else {
            MissionGenerationReleaseResult(MissionGenerationLatencyOutcome.RETRY)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MissionGenerationWorkService::class.java)
        private val DEFAULT_LEASE_DURATION: Duration = Duration.ofMinutes(10)
        private const val MAX_ALTERNATIVES = 3
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_ATTEMPTS = 5
        private val DRAFT_TTL: Duration = Duration.ofHours(24)
    }
}

data class MissionGenerationWork(
    val jobId: UUID,
    val guestUserId: Long,
    val item: MissionItem,
    val baselineFrequency: Int,
    val baselineAmountWon: Int,
    val birthDate: LocalDate,
    val address: ResidentialArea?,
    val leaseToken: UUID,
)

data class MissionGenerationCompletion(
    val createdAt: java.time.Instant,
    val completedAt: java.time.Instant,
    val source: MissionDraftGenerationSource?,
)

data class MissionGenerationReleaseResult(
    val outcome: MissionGenerationLatencyOutcome,
    val completion: MissionGenerationCompletion? = null,
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
