package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionDraft
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalRequest
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
    private val alternativeGenerator: MissionAlternativeGenerationPort,
) {
    fun execute(jobId: UUID): MissionGenerationExecutionResult {
        val work = when (val preparation = workService.prepare(jobId)) {
            is MissionGenerationPreparation.Claimed -> preparation.work
            MissionGenerationPreparation.ActiveLease -> return MissionGenerationExecutionResult.ACTIVE_LEASE
            MissionGenerationPreparation.Skipped -> return MissionGenerationExecutionResult.SKIPPED
        }
        try {
            val query = MissionSearchQueryFactory.create(
                item = work.item,
                birthDate = work.birthDate,
                address = work.address,
                today = LocalDate.now(workService.clock),
                baselineFrequency = work.baselineFrequency,
                baselineAmountWon = work.baselineAmountWon,
            )
            val retrieved = runCatching {
                knowledgeRetrievalPort.retrieve(
                    MissionKnowledgeRetrievalRequest(
                        jobId = work.jobId,
                        item = work.item,
                        queryText = query,
                        today = LocalDate.now(workService.clock),
                    ),
                )
            }.onFailure { exception ->
                log.warn("mission_generation.knowledge_retrieval.failed item={}", work.item, exception)
            }.getOrNull()
            val verifiedIds = runCatching {
                knowledgeVerificationPort.verify(retrieved?.knowledge.orEmpty())
                    .mapTo(mutableSetOf()) { it.id }
            }.onFailure { exception ->
                log.warn("mission_generation.knowledge_verification.failed item={}", work.item, exception)
            }.getOrDefault(emptySet())
            val verifiedKnowledge = retrieved?.knowledge.orEmpty()
                .filter { it.id in verifiedIds }
                .take(MAX_KNOWLEDGE_CONTEXTS)
            if (retrieved != null) {
                log.info(
                    "mission_generation.knowledge.used item={} candidateCount={} selectedCount={} policy={}",
                    work.item,
                    retrieved.candidateCount,
                    verifiedKnowledge.size,
                    retrieved.policy,
                )
            }
            val generated = alternativeGenerator.generate(
                MissionAlternativeGenerationRequest(work.item, verifiedKnowledge),
            )
            workService.complete(work, generated)
            return MissionGenerationExecutionResult.COMPLETED
        } catch (exception: Exception) {
            workService.releaseOrFail(work)
            throw exception
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MissionGenerationExecutor::class.java)
        private const val MAX_KNOWLEDGE_CONTEXTS = 5
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
    ) {
        val job = jobRepository.findByIdForUpdate(work.jobId) ?: return
        if (!job.ownsLease(work.leaseToken, clock.instant())) return
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
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun releaseOrFail(work: MissionGenerationWork) {
        jobRepository.findByIdForUpdate(work.jobId)
            ?.releaseOrFail(work.leaseToken, clock.instant(), MAX_ATTEMPTS)
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
