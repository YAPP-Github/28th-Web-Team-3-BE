package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ConfirmationResult
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationOutbox
import backend.yapp.core.mission.generation.domain.MissionGenerationOutboxRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.goal.domain.GoalRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionGenerationService(
    private val jobRepository: MissionGenerationJobRepository,
    private val draftRepository: MissionDraftRepository,
    private val missionRepository: MissionRepository,
    private val goalRepository: GoalRepository,
    private val surveyRepository: MissionSurveyRepository,
    private val clock: Clock,
    private val outboxRepository: MissionGenerationOutboxRepository,
) {
    @Transactional
    fun request(guestUserId: Long): MissionGenerationJobSnapshot {
        if (goalRepository.findByGuestUserId(guestUserId) == null) {
            throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)
        }
        if (surveyRepository.findByGuestUserId(guestUserId) == null) {
            throw BaseException(ErrorCode.MISSION_SURVEY_NOT_FOUND)
        }

        val existing = jobRepository.findFirstByGuestUserIdAndActiveGenerationKeyOrderByCreatedAtDesc(
            guestUserId,
            MissionGenerationJob.ACTIVE_KEY,
        )
        if (existing != null) return existing.toSnapshot()

        val now = clock.instant()
        val job = jobRepository.saveAndFlush(
            MissionGenerationJob(
                id = UUID.randomUUID(),
                guestUserId = guestUserId,
                createdAt = now,
            ),
        )
        outboxRepository.save(
            MissionGenerationOutbox(
                id = UUID.randomUUID(),
                jobId = job.id,
                nextAttemptAt = now,
                createdAt = now,
            ),
        )
        return job.toSnapshot()
    }

    @Transactional(readOnly = true)
    fun status(guestUserId: Long, jobId: UUID): MissionGenerationJobSnapshot =
        ownedJob(guestUserId, jobId).toSnapshot()

    @Transactional(readOnly = true)
    fun drafts(guestUserId: Long, jobId: UUID): List<MissionDraftSnapshot> {
        val job = ownedJob(guestUserId, jobId)
        ensureDraftsAvailable(job)
        return draftRepository.findAllByJobIdOrderByCategoryAscCreatedAtAsc(jobId).map { draft ->
            MissionDraftSnapshot(
                id = draft.id,
                category = draft.category,
                title = draft.title,
                description = draft.description,
                actionCode = draft.actionCode,
                metricType = draft.metricType,
                targetCount = draft.targetCount,
                targetUnit = draft.targetUnit,
                estimatedSavingsWon = draft.estimatedSavingsWon,
                savingsEstimateVersion = draft.savingsEstimateVersion,
            )
        }
    }

    @Transactional
    fun confirm(guestUserId: Long, jobId: UUID, selectedDraftIds: List<UUID>): List<MissionSnapshot> {
        if (selectedDraftIds.size < MIN_SELECTION ||
            selectedDraftIds.distinct().size != selectedDraftIds.size
        ) {
            throw BaseException(ErrorCode.MISSION_CONFIRM_INVALID)
        }

        val job = jobRepository.findByIdAndGuestUserIdForUpdate(jobId, guestUserId)
            ?: throw BaseException(ErrorCode.MISSION_GENERATION_JOB_NOT_FOUND)
        ensureDraftsAvailable(job)

        val fingerprint = fingerprint(selectedDraftIds)
        when (job.confirm(fingerprint, clock.instant())) {
            ConfirmationResult.IDEMPOTENT -> return missionRepository
                .findAllByJobIdOrderByCreatedAtAsc(jobId)
                .map { it.toSnapshot() }
            ConfirmationResult.CONFLICT -> throw BaseException(ErrorCode.MISSION_CONFIRM_CONFLICT)
            ConfirmationResult.CREATED -> Unit
        }

        val draftsById = draftRepository.findAllByJobIdAndIdIn(jobId, selectedDraftIds).associateBy { it.id }
        if (draftsById.size != selectedDraftIds.size) {
            throw BaseException(ErrorCode.MISSION_CONFIRM_INVALID)
        }

        val now = clock.instant()
        val missions = selectedDraftIds.map { draftId ->
            val draft = draftsById.getValue(draftId)
            Mission(
                id = UUID.randomUUID(),
                jobId = jobId,
                draftId = draft.id,
                guestUserId = guestUserId,
                category = draft.category,
                title = draft.title,
                description = draft.description,
                actionCode = draft.actionCode,
                metricType = draft.metricType,
                targetCount = draft.targetCount,
                targetUnit = draft.targetUnit,
                estimatedSavingsWon = draft.estimatedSavingsWon,
                savingsEstimateVersion = draft.savingsEstimateVersion,
                weekEndsAt = weekEnd(now),
                createdAt = now,
            )
        }
        return missionRepository.saveAll(missions).map { it.toSnapshot() }
    }

    private fun ownedJob(guestUserId: Long, jobId: UUID): MissionGenerationJob =
        jobRepository.findByIdAndGuestUserId(jobId, guestUserId)
            ?: throw BaseException(ErrorCode.MISSION_GENERATION_JOB_NOT_FOUND)

    private fun ensureDraftsAvailable(job: MissionGenerationJob) {
        when (job.status) {
            MissionGenerationJobStatus.PENDING,
            MissionGenerationJobStatus.RUNNING,
            -> throw BaseException(ErrorCode.MISSION_GENERATION_NOT_READY)
            MissionGenerationJobStatus.FAILED -> throw BaseException(ErrorCode.MISSION_GENERATION_FAILED)
            MissionGenerationJobStatus.SUCCEEDED -> if (job.isExpired(clock.instant())) {
                throw BaseException(ErrorCode.MISSION_DRAFT_EXPIRED)
            }
        }
    }

    private fun MissionGenerationJob.toSnapshot(): MissionGenerationJobSnapshot =
        MissionGenerationJobSnapshot(
            jobId = id,
            status = status,
            failureCode = failureCode,
            generationSource = generationSource,
            draftsAvailable = status == MissionGenerationJobStatus.SUCCEEDED && !isExpired(clock.instant()),
            expiresAt = expiresAt,
            confirmed = confirmedAt != null,
        )

    private fun Mission.toSnapshot(): MissionSnapshot =
        MissionSnapshot(
            id = id,
            category = category,
            title = title,
            description = description,
            actionCode = actionCode,
            metricType = metricType,
            targetCount = targetCount,
            targetUnit = targetUnit,
            estimatedSavingsWon = estimatedSavingsWon,
            savingsEstimateVersion = savingsEstimateVersion,
            status = status.name,
        )

    private fun fingerprint(ids: List<UUID>): String {
        val canonical = ids.map(UUID::toString).sorted().joinToString(",")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun weekEnd(now: Instant): Instant {
        val zone = ZoneId.of("Asia/Seoul")
        val date = now.atZone(zone).toLocalDate()
        return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
    }

    companion object {
        private const val MIN_SELECTION = 1
    }
}
