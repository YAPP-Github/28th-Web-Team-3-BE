package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMission
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletion
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletionRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionLifecycleService(
    private val missionRepository: MissionRepository,
    private val manualRepository: ManualMissionRepository,
    private val weeklyCompletionRepository: MissionWeeklyCompletionRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(
        guestUserId: Long,
        status: MissionStatus?,
        category: MissionCategory? = null,
    ): List<LifecycleMissionSnapshot> {
        val weekStart = currentWeekStart()
        val completedKeys = weeklyCompletionRepository
            .findAllByGuestUserIdAndWeekStartDate(guestUserId, weekStart)
            .mapTo(mutableSetOf()) { it.missionSource to it.missionId }
        return (
            missionRepository.findAllByGuestUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(guestUserId)
                .map { it.toSnapshot(completedKeys.contains(MissionSource.RECOMMENDED.name to it.id), weekStart) } +
                manualRepository.findAllByGuestUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(guestUserId)
                    .map { it.toSnapshot(completedKeys.contains(MissionSource.MANUAL.name to it.id), weekStart) }
            ).asSequence()
            .filter { it.item?.active != false }
            .filter { category == null || it.category == category }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    @Transactional(readOnly = true)
    fun progress(guestUserId: Long, category: MissionCategory? = null): MissionProgressSnapshot {
        val missions = list(guestUserId, null, category)
        val completedCount = missions.count { it.status == MissionStatus.COMPLETED }
        val totalCount = missions.size
        return MissionProgressSnapshot(
            completedCount = completedCount,
            totalCount = totalCount,
            progressPercent = if (totalCount == 0) 0 else completedCount * 100 / totalCount,
            weekStartDate = currentWeekStart(),
        )
    }

    @Transactional
    fun createManual(
        guestUserId: Long,
        category: MissionCategory,
        text: String,
    ): LifecycleMissionSnapshot {
        val normalizedText = text.trim()
        if (!category.active || normalizedText.isEmpty() || normalizedText.length > MAX_MANUAL_MISSION_TEXT_LENGTH) {
            throw BaseException(ErrorCode.MANUAL_MISSION_INVALID)
        }
        val now = clock.instant()
        val weekStart = currentWeekStart()
        return manualRepository.save(
            ManualMission(
                id = UUID.randomUUID(),
                guestUserId = guestUserId,
                category = category,
                missionText = normalizedText,
                weekEndsAt = weekEnd(weekStart),
                createdAt = now,
            ),
        ).toSnapshot(completed = false, weekStart = weekStart)
    }

    @Transactional
    fun delete(guestUserId: Long, source: MissionSource, missionId: UUID) {
        val now = clock.instant()
        when (source) {
            MissionSource.RECOMMENDED -> activeMission(guestUserId, missionId).softDelete(now)
            MissionSource.MANUAL -> activeManualMission(guestUserId, missionId).softDelete(now)
        }
    }

    @Transactional
    fun complete(guestUserId: Long, source: MissionSource, missionId: UUID): LifecycleMissionSnapshot {
        val now = clock.instant()
        val weekStart = currentWeekStart()
        val snapshot = when (source) {
            MissionSource.RECOMMENDED -> activeMission(guestUserId, missionId).toSnapshot(true, weekStart)
            MissionSource.MANUAL -> activeManualMission(guestUserId, missionId).toSnapshot(true, weekStart)
        }
        if (weeklyCompletionRepository.findByMissionSourceAndMissionIdAndWeekStartDate(
                source.name,
                missionId,
                weekStart,
            ) == null
        ) {
            weeklyCompletionRepository.save(
                MissionWeeklyCompletion(
                    id = UUID.randomUUID(),
                    guestUserId = guestUserId,
                    missionSource = source.name,
                    missionId = missionId,
                    weekStartDate = weekStart,
                    completedAt = now,
                ),
            )
        }
        return snapshot
    }

    @Deprecated("Weekly completion rows make overdue transitions unnecessary")
    fun markOverdueIncomplete(): Int = 0

    @Deprecated("Weekly completion rows make overdue transitions unnecessary")
    fun markOverdueIncomplete(now: Instant): Int = 0

    private fun activeMission(guestUserId: Long, missionId: UUID): Mission =
        missionRepository.findByIdAndGuestUserIdForUpdate(missionId, guestUserId)
            ?.takeIf { it.deletedAt == null && it.item?.active != false }
            ?: throw BaseException(ErrorCode.MISSION_NOT_FOUND)

    private fun activeManualMission(guestUserId: Long, missionId: UUID): ManualMission =
        manualRepository.findByIdAndGuestUserIdForUpdate(missionId, guestUserId)
            ?.takeIf { it.deletedAt == null }
            ?: throw BaseException(ErrorCode.MISSION_NOT_FOUND)

    private fun currentWeekStart(): LocalDate =
        clock.instant().atZone(SEOUL).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun weekEnd(weekStart: LocalDate): Instant = weekStart.plusWeeks(1).atStartOfDay(SEOUL).toInstant()

    private fun Mission.toSnapshot(completed: Boolean, weekStart: LocalDate) = LifecycleMissionSnapshot(
        id = id,
        source = MissionSource.RECOMMENDED,
        category = category,
        item = item,
        title = title,
        targetCount = targetCount,
        targetUnit = targetUnit,
        estimatedSavingsWon = estimatedSavingsWon,
        savingsEstimateVersion = savingsEstimateVersion,
        status = if (completed) MissionStatus.COMPLETED else MissionStatus.ACTIVE,
        weekEndsAt = weekEnd(weekStart),
        createdAt = createdAt,
    )

    private fun ManualMission.toSnapshot(completed: Boolean, weekStart: LocalDate) = LifecycleMissionSnapshot(
        id = id,
        source = MissionSource.MANUAL,
        category = category,
        item = null,
        title = missionText,
        targetCount = null,
        targetUnit = null,
        estimatedSavingsWon = null,
        savingsEstimateVersion = null,
        status = if (completed) MissionStatus.COMPLETED else MissionStatus.ACTIVE,
        weekEndsAt = weekEnd(weekStart),
        createdAt = createdAt,
    )

    companion object {
        private const val MAX_MANUAL_MISSION_TEXT_LENGTH = 30
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

enum class MissionSource {
    RECOMMENDED,
    MANUAL,
}

data class LifecycleMissionSnapshot(
    val id: UUID,
    val source: MissionSource,
    val category: MissionCategory,
    val item: MissionItem?,
    val title: String,
    val targetCount: Int?,
    val targetUnit: String?,
    val estimatedSavingsWon: Int?,
    val savingsEstimateVersion: String?,
    val status: MissionStatus,
    val weekEndsAt: Instant,
    val createdAt: Instant,
)

data class MissionProgressSnapshot(
    val completedCount: Int,
    val totalCount: Int,
    val progressPercent: Int,
    val weekStartDate: LocalDate,
)
