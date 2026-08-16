package backend.yapp.core.mission.generation.service

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionWeekCalendarTest {
    private val calendar = MissionWeekCalendar(
        Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `august 2026 contains four monday based weeks whose thursdays belong to august`() {
        val weeks = calendar.weeksOf(YearMonth.of(2026, 8))

        assertEquals(4, weeks.size)
        assertEquals("2026-08-03", weeks.first().weekStartDate.toString())
        assertEquals("2026-08-09", weeks.first().weekEndDate.toString())
        assertEquals("2026-08-24", weeks.last().weekStartDate.toString())
        assertEquals("2026-08-30", weeks.last().weekEndDate.toString())
    }

    @Test
    fun `january week can start in the previous year and five weeks are returned`() {
        val weeks = calendar.weeksOf(YearMonth.of(2026, 1))

        assertEquals(5, weeks.size)
        assertEquals("2025-12-29", weeks.first().weekStartDate.toString())
        assertEquals("2026-02-01", weeks.last().weekEndDate.toString())
    }

    @Test
    fun `current history month follows the thursday of the current week`() {
        assertEquals(YearMonth.of(2026, 9), calendar.currentHistoryMonth())
        assertEquals("2026-08-31", calendar.currentWeekStart().toString())
    }

    @Test
    fun `leap year february uses the same thursday ownership rule`() {
        val weeks = calendar.weeksOf(YearMonth.of(2028, 2))

        assertEquals(4, weeks.size)
        assertEquals("2028-01-31", weeks.first().weekStartDate.toString())
        assertEquals("2028-02-27", weeks.last().weekEndDate.toString())
    }
}
