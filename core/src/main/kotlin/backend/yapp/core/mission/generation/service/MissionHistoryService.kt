package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionHistoryService(
    private val missionRepository: MissionRepository,
    private val manualMissionRepository: ManualMissionRepository,
    private val weeklyCompletionRepository: MissionWeeklyCompletionRepository,
    private val clock: Clock,
    private val weekCalendar: MissionWeekCalendar,
) {
    @Transactional(readOnly = true)
    fun histories(guestUserId: Long, requestedMonth: YearMonth): List<MissionWeeklyHistorySnapshot> {
        val now = clock.instant()
        validatePeriod(requestedMonth, now)

        val weeks = weekCalendar.weeksOf(requestedMonth)
        val rangeStart = weekCalendar.weekStartInstant(weeks.first().weekStartDate)
        val rangeEnd = weekCalendar.weekEndExclusive(weeks.last().weekStartDate)
        val missions = missionRepository.findAllOverlappingHistoryPeriod(guestUserId, rangeStart, rangeEnd)
            .map { HistoryMission(it.id, MissionSource.RECOMMENDED, it.createdAt, it.deletedAt) }
        val manualMissions = manualMissionRepository.findAllOverlappingHistoryPeriod(guestUserId, rangeStart, rangeEnd)
            .map { HistoryMission(it.id, MissionSource.MANUAL, it.createdAt, it.deletedAt) }
        val historyMissions = missions + manualMissions
        val completions = weeklyCompletionRepository
            .findAllByGuestUserIdAndWeekStartDateIn(guestUserId, weeks.map { it.weekStartDate })
            .groupBy { it.weekStartDate }
        val currentWeekStart = weekCalendar.currentWeekStart(now)

        return weeks.map { week ->
            val isCurrentWeek = week.weekStartDate == currentWeekStart
            if (week.weekStartDate < HISTORY_AVAILABLE_FROM_WEEK || week.weekStartDate > currentWeekStart) {
                return@map week.emptySnapshot(isCurrentWeek)
            }

            val weekEndExclusive = weekCalendar.weekEndExclusive(week.weekStartDate)
            val includedMissions = historyMissions.filter { mission ->
                if (isCurrentWeek) {
                    mission.createdAt <= now && mission.deletedAt == null
                } else {
                    mission.createdAt < weekEndExclusive &&
                        (mission.deletedAt == null || mission.deletedAt >= weekEndExclusive)
                }
            }
            val includedMissionKeys = includedMissions.mapTo(mutableSetOf()) { it.source.name to it.id }
            val completedCount = completions[week.weekStartDate].orEmpty().count { completion ->
                completion.guestUserId == guestUserId &&
                    completion.completedAt >= weekCalendar.weekStartInstant(week.weekStartDate) &&
                    completion.completedAt < weekEndExclusive &&
                    includedMissionKeys.contains(completion.missionSource to completion.missionId)
            }

            MissionWeeklyHistorySnapshot(
                weekOfMonth = week.weekOfMonth,
                weekStartDate = week.weekStartDate,
                weekEndDate = week.weekEndDate,
                completedCount = completedCount,
                totalCount = includedMissions.size,
                isCurrentWeek = isCurrentWeek,
            )
        }
    }

    private fun validatePeriod(requestedMonth: YearMonth, now: Instant) {
        if (requestedMonth < HISTORY_AVAILABLE_FROM_MONTH) {
            throw BaseException(ErrorCode.MISSION_HISTORY_NOT_AVAILABLE)
        }
        if (requestedMonth > weekCalendar.currentHistoryMonth(now)) {
            throw BaseException(ErrorCode.MISSION_HISTORY_INVALID_PERIOD)
        }
    }

    private fun MissionCalendarWeek.emptySnapshot(isCurrentWeek: Boolean) = MissionWeeklyHistorySnapshot(
        weekOfMonth = weekOfMonth,
        weekStartDate = weekStartDate,
        weekEndDate = weekEndDate,
        completedCount = 0,
        totalCount = 0,
        isCurrentWeek = isCurrentWeek,
    )

    companion object {
        // V19 reached production during the second August week. Product policy represents
        // August weeks 1-2 as synthetic 0/0 and exposes recorded aggregates from week 3.
        val HISTORY_AVAILABLE_FROM_WEEK: LocalDate = LocalDate.of(2026, 8, 17)
        private val HISTORY_AVAILABLE_FROM_MONTH: YearMonth = YearMonth.from(HISTORY_AVAILABLE_FROM_WEEK)
    }
}

private data class HistoryMission(
    val id: UUID,
    val source: MissionSource,
    val createdAt: Instant,
    val deletedAt: Instant?,
)

data class MissionWeeklyHistorySnapshot(
    val weekOfMonth: Int,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val isCurrentWeek: Boolean,
)
