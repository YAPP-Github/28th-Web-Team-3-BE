package backend.yapp.core.mission.generation.domain

import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MissionGenerationJobRepository : JpaRepository<MissionGenerationJob, UUID> {
    fun findFirstByGuestUserIdAndActiveGenerationKeyOrderByCreatedAtDesc(
        guestUserId: Long,
        activeGenerationKey: String,
    ): MissionGenerationJob?

    fun findByIdAndGuestUserId(id: UUID, guestUserId: Long): MissionGenerationJob?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select job from MissionGenerationJob job " +
            "where job.id = :id and job.guestUserId = :guestUserId",
    )
    fun findByIdAndGuestUserIdForUpdate(
        @Param("id") id: UUID,
        @Param("guestUserId") guestUserId: Long,
    ): MissionGenerationJob?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from MissionGenerationJob job where job.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): MissionGenerationJob?

    @Modifying
    @Query(
        "update MissionGenerationJob job set job.status = :failed, job.failureCode = :failureCode, " +
            "job.activeGenerationKey = null, job.updatedAt = :now, " +
            "job.version = job.version + 1 " +
            "where job.status in :activeStatuses and job.updatedAt < :cutoff",
    )
    fun failStaleActive(
        @Param("activeStatuses") activeStatuses: Collection<MissionGenerationJobStatus>,
        @Param("failed") failed: MissionGenerationJobStatus,
        @Param("failureCode") failureCode: String,
        @Param("cutoff") cutoff: Instant,
        @Param("now") now: Instant,
    ): Int
}

interface MissionDraftTemplateRepository : JpaRepository<MissionDraftTemplate, Long> {
    fun findByCategoryInAndActiveTrueOrderByCategoryAscSortOrderAsc(
        categories: Collection<MissionCategory>,
    ): List<MissionDraftTemplate>
}

interface MissionDraftRepository : JpaRepository<MissionDraft, UUID> {
    fun findAllByJobIdOrderByCategoryAscCreatedAtAsc(jobId: UUID): List<MissionDraft>
    fun findAllByJobIdAndIdIn(jobId: UUID, ids: Collection<UUID>): List<MissionDraft>
}

interface MissionRepository : JpaRepository<Mission, UUID> {
    fun findAllByJobIdOrderByCreatedAtAsc(jobId: UUID): List<Mission>
    fun findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId: Long): List<Mission>
    fun findByIdAndGuestUserId(id: UUID, guestUserId: Long): Mission?
    fun findAllByStatusAndWeekEndsAtLessThanEqual(status: MissionStatus, cutoff: Instant): List<Mission>
}

interface ManualMissionRepository : JpaRepository<ManualMission, UUID> {
    fun findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId: Long): List<ManualMission>
    fun findByIdAndGuestUserId(id: UUID, guestUserId: Long): ManualMission?
    fun findAllByStatusAndWeekEndsAtLessThanEqual(status: MissionStatus, cutoff: Instant): List<ManualMission>
}

interface MissionOutcomeEventRepository : JpaRepository<MissionOutcomeEvent, UUID> {
    fun findAllByGuestUserIdOrderByOccurredAtDesc(guestUserId: Long): List<MissionOutcomeEvent>
}

interface MissionRecommendationSnapshotRepository : JpaRepository<MissionRecommendationSnapshot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByGuestUserIdAndJobIdIsNullOrderByCreatedAtDesc(guestUserId: Long): MissionRecommendationSnapshot?
    fun findByJobId(jobId: UUID): MissionRecommendationSnapshot?
}

interface MissionRecommendationCandidateTraceRepository :
    JpaRepository<MissionRecommendationCandidateTrace, UUID> {
    fun findAllBySnapshotId(snapshotId: UUID): List<MissionRecommendationCandidateTrace>
}
