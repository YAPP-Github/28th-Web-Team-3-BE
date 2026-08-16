package backend.yapp.core.mission.generation.service

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import org.springframework.stereotype.Component

@Component
class MissionWeekCalendar(
    private val clock: Clock,
) {
    fun currentWeekStart(): LocalDate = currentWeekStart(clock.instant())

    fun currentWeekStart(now: Instant): LocalDate =
        now.atZone(SEOUL).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun currentHistoryMonth(now: Instant = clock.instant()): YearMonth =
        YearMonth.from(currentWeekStart(now).plusDays(DAYS_FROM_MONDAY_TO_THURSDAY))

    fun weeksOf(month: YearMonth): List<MissionCalendarWeek> {
        var thursday = month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
        val weeks = mutableListOf<MissionCalendarWeek>()
        while (YearMonth.from(thursday) == month) {
            val weekStartDate = thursday.minusDays(DAYS_FROM_MONDAY_TO_THURSDAY)
            weeks += MissionCalendarWeek(
                weekOfMonth = weeks.size + 1,
                weekStartDate = weekStartDate,
                weekEndDate = weekStartDate.plusDays(DAYS_FROM_MONDAY_TO_SUNDAY),
            )
            thursday = thursday.plusWeeks(1)
        }
        return weeks
    }

    fun weekStartInstant(weekStartDate: LocalDate): Instant = weekStartDate.atStartOfDay(SEOUL).toInstant()

    fun weekEndExclusive(weekStartDate: LocalDate): Instant =
        weekStartDate.plusWeeks(1).atStartOfDay(SEOUL).toInstant()

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val DAYS_FROM_MONDAY_TO_THURSDAY = 3L
        private const val DAYS_FROM_MONDAY_TO_SUNDAY = 6L
    }
}

data class MissionCalendarWeek(
    val weekOfMonth: Int,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
)
