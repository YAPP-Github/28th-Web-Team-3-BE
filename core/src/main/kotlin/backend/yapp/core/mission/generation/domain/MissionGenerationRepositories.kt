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
    @Modifying
    @Query("delete from MissionGenerationJob job where job.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
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

    @Query(
        value = "select id from mission_generation_job where status = 'RUNNING' " +
            "and (lease_expires_at is null or lease_expires_at <= :now) " +
            "order by updated_at asc limit 100",
        nativeQuery = true,
    )
    fun findRecoverableRunningIds(@Param("now") now: Instant): List<UUID>

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

interface MissionGenerationOutboxRepository : JpaRepository<MissionGenerationOutbox, UUID> {
    fun findByJobIdAndGeneration(jobId: UUID, generation: Int): MissionGenerationOutbox?

    @Query(
        value = "select * from mission_generation_outbox " +
            "where status in ('CREATED', 'CLAIMED') and next_attempt_at <= :now " +
            "order by created_at asc limit 100 for update skip locked",
        nativeQuery = true,
    )
    fun findDueForUpdate(@Param("now") now: Instant): List<MissionGenerationOutbox>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from MissionGenerationOutbox outbox where outbox.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): MissionGenerationOutbox?

    fun findTopByJobIdOrderByGenerationDesc(jobId: UUID): MissionGenerationOutbox?
}

interface MissionDraftTemplateRepository : JpaRepository<MissionDraftTemplate, Long> {
    fun findByCategoryInAndActiveTrueOrderByCategoryAscSortOrderAsc(
        categories: Collection<MissionCategory>,
    ): List<MissionDraftTemplate>
    fun findByTargetCodeAndActiveTrue(targetCode: String): MissionDraftTemplate?
}

interface MissionDraftRepository : JpaRepository<MissionDraft, UUID> {
    @Modifying
    @Query("delete from MissionDraft draft where draft.jobId in (select job.id from MissionGenerationJob job where job.guestUserId = :guestUserId)")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    fun findAllByJobIdOrderByCategoryAscCreatedAtAsc(jobId: UUID): List<MissionDraft>
    fun findAllByJobIdAndIdIn(jobId: UUID, ids: Collection<UUID>): List<MissionDraft>
}

interface MissionRepository : JpaRepository<Mission, UUID> {
    @Modifying
    @Query("delete from Mission mission where mission.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    fun findAllByJobIdOrderByCreatedAtAsc(jobId: UUID): List<Mission>
    fun findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId: Long): List<Mission>
    fun findAllByGuestUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(guestUserId: Long): List<Mission>
    fun findByIdAndGuestUserId(id: UUID, guestUserId: Long): Mission?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from Mission mission where mission.id = :id and mission.guestUserId = :guestUserId")
    fun findByIdAndGuestUserIdForUpdate(
        @Param("id") id: UUID,
        @Param("guestUserId") guestUserId: Long,
    ): Mission?
    fun findAllByStatusAndWeekEndsAtLessThanEqual(status: MissionStatus, cutoff: Instant): List<Mission>
}

interface ManualMissionRepository : JpaRepository<ManualMission, UUID> {
    @Modifying
    @Query("delete from ManualMission mission where mission.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    fun findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId: Long): List<ManualMission>
    fun findAllByGuestUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(guestUserId: Long): List<ManualMission>
    fun findByIdAndGuestUserId(id: UUID, guestUserId: Long): ManualMission?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from ManualMission mission where mission.id = :id and mission.guestUserId = :guestUserId")
    fun findByIdAndGuestUserIdForUpdate(
        @Param("id") id: UUID,
        @Param("guestUserId") guestUserId: Long,
    ): ManualMission?
    fun findAllByStatusAndWeekEndsAtLessThanEqual(status: MissionStatus, cutoff: Instant): List<ManualMission>
}

interface MissionOutcomeEventRepository : JpaRepository<MissionOutcomeEvent, UUID> {
    @Modifying
    @Query("delete from MissionOutcomeEvent event where event.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    fun findAllByGuestUserIdOrderByOccurredAtDesc(guestUserId: Long): List<MissionOutcomeEvent>
}

interface MissionRecommendationSnapshotRepository : JpaRepository<MissionRecommendationSnapshot, UUID> {
    @Modifying
    @Query("delete from MissionRecommendationSnapshot snapshot where snapshot.guestUserId = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByGuestUserIdAndJobIdIsNullOrderByCreatedAtDesc(guestUserId: Long): MissionRecommendationSnapshot?
    fun findByJobId(jobId: UUID): MissionRecommendationSnapshot?
}

interface MissionRecommendationCandidateTraceRepository :
    JpaRepository<MissionRecommendationCandidateTrace, UUID> {
    @Modifying
    @Query("delete from MissionRecommendationCandidateTrace candidate where candidate.snapshotId in (select snapshot.id from MissionRecommendationSnapshot snapshot where snapshot.guestUserId = :guestUserId)")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int
    fun findAllBySnapshotId(snapshotId: UUID): List<MissionRecommendationCandidateTrace>
}
