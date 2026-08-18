package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMission
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletion
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MissionHistoryServiceTest {
    @Test
    fun `history returns initial synthetic zero weeks and actual aggregates from august third week`() {
        val fixture = fixture()
        val week3Start = LocalDate.of(2026, 8, 17)
        val week4Start = LocalDate.of(2026, 8, 24)
        val week3End = Instant.parse("2026-08-23T15:00:00Z")
        val activeRecommended = recommendedMission(createdAt = Instant.parse("2026-08-16T15:00:00Z"))
        val deletedAtBoundary = recommendedMission(
            createdAt = Instant.parse("2026-08-17T00:00:00Z"),
            deletedAt = week3End,
        )
        val deletedBeforeBoundary = recommendedMission(
            createdAt = Instant.parse("2026-08-17T00:00:00Z"),
            deletedAt = Instant.parse("2026-08-23T02:00:00Z"),
        )
        val activeManual = manualMission(createdAt = Instant.parse("2026-08-24T00:00:00Z"))
        `when`(
            fixture.missionRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-02T15:00:00Z"),
                Instant.parse("2026-08-30T15:00:00Z"),
            ),
        ).thenReturn(listOf(activeRecommended, deletedAtBoundary, deletedBeforeBoundary))
        `when`(
            fixture.manualRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-02T15:00:00Z"),
                Instant.parse("2026-08-30T15:00:00Z"),
            ),
        ).thenReturn(listOf(activeManual))
        `when`(
            fixture.completionRepository.findAllByGuestUserIdAndWeekStartDateIn(
                GUEST_USER_ID,
                listOf(
                    LocalDate.of(2026, 8, 3),
                    LocalDate.of(2026, 8, 10),
                    week3Start,
                    week4Start,
                ),
            ),
        ).thenReturn(
            listOf(
                completion(activeRecommended.id, MissionSource.RECOMMENDED, week3Start, "2026-08-18T00:00:00Z"),
                completion(deletedAtBoundary.id, MissionSource.RECOMMENDED, week3Start, "2026-08-19T00:00:00Z"),
                completion(deletedBeforeBoundary.id, MissionSource.RECOMMENDED, week3Start, "2026-08-20T00:00:00Z"),
                completion(activeManual.id, MissionSource.MANUAL, week4Start, "2026-08-25T00:00:00Z"),
            ),
        )

        val histories = fixture.service.histories(GUEST_USER_ID, YearMonth.of(2026, 8))

        assertEquals(listOf(0, 0, 2, 1), histories.map { it.completedCount })
        assertEquals(listOf(0, 0, 2, 2), histories.map { it.totalCount })
        assertFalse(histories.any { it.isCurrentWeek })
    }

    @Test
    fun `current week uses only missions active now and future weeks stay zero`() {
        val fixture = fixture(now = Instant.parse("2026-09-02T03:00:00Z"))
        val activeMission = recommendedMission(createdAt = Instant.parse("2026-08-31T00:00:00Z"))
        val deletedMission = recommendedMission(
            createdAt = Instant.parse("2026-08-31T00:00:00Z"),
            deletedAt = Instant.parse("2026-09-01T00:00:00Z"),
        )
        `when`(
            fixture.missionRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-09-27T15:00:00Z"),
            ),
        ).thenReturn(listOf(activeMission, deletedMission))
        `when`(
            fixture.manualRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-09-27T15:00:00Z"),
            ),
        ).thenReturn(emptyList())
        val weekStarts = listOf(
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 21),
        )
        `when`(fixture.completionRepository.findAllByGuestUserIdAndWeekStartDateIn(GUEST_USER_ID, weekStarts))
            .thenReturn(
                listOf(
                    completion(
                        activeMission.id,
                        MissionSource.RECOMMENDED,
                        weekStarts.first(),
                        "2026-09-01T01:00:00Z",
                    ),
                ),
            )

        val histories = fixture.service.histories(GUEST_USER_ID, YearMonth.of(2026, 9))

        assertEquals(listOf(1, 0, 0, 0), histories.map { it.completedCount })
        assertEquals(listOf(1, 0, 0, 0), histories.map { it.totalCount })
        assertEquals(listOf(true, false, false, false), histories.map { it.isCurrentWeek })
    }

    @Test
    fun `period before august or after current history month is rejected`() {
        val fixture = fixture()

        val unavailable = assertFailsWith<BaseException> {
            fixture.service.histories(GUEST_USER_ID, YearMonth.of(2026, 7))
        }
        val future = assertFailsWith<BaseException> {
            fixture.service.histories(GUEST_USER_ID, YearMonth.of(2026, 10))
        }

        assertEquals(ErrorCode.MISSION_HISTORY_NOT_AVAILABLE, unavailable.errorCode)
        assertEquals(ErrorCode.MISSION_HISTORY_INVALID_PERIOD, future.errorCode)
    }

    @Test
    fun `august second week keeps current flag but returns synthetic zero counts`() {
        val fixture = fixture(now = Instant.parse("2026-08-16T03:00:00Z"))
        `when`(
            fixture.missionRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-02T15:00:00Z"),
                Instant.parse("2026-08-30T15:00:00Z"),
            ),
        ).thenReturn(emptyList())
        `when`(
            fixture.manualRepository.findAllOverlappingHistoryPeriod(
                GUEST_USER_ID,
                Instant.parse("2026-08-02T15:00:00Z"),
                Instant.parse("2026-08-30T15:00:00Z"),
            ),
        ).thenReturn(emptyList())
        `when`(
            fixture.completionRepository.findAllByGuestUserIdAndWeekStartDateIn(
                GUEST_USER_ID,
                listOf(
                    LocalDate.of(2026, 8, 3),
                    LocalDate.of(2026, 8, 10),
                    LocalDate.of(2026, 8, 17),
                    LocalDate.of(2026, 8, 24),
                ),
            ),
        ).thenReturn(emptyList())

        val histories = fixture.service.histories(GUEST_USER_ID, YearMonth.of(2026, 8))

        assertEquals(0, histories[1].completedCount)
        assertEquals(0, histories[1].totalCount)
        assertEquals(true, histories[1].isCurrentWeek)
    }

    private fun fixture(now: Instant = NOW): Fixture {
        val missionRepository = mock(MissionRepository::class.java)
        val manualRepository = mock(ManualMissionRepository::class.java)
        val completionRepository = mock(MissionWeeklyCompletionRepository::class.java)
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val calendar = MissionWeekCalendar(clock)
        return Fixture(
            MissionHistoryService(missionRepository, manualRepository, completionRepository, clock, calendar),
            missionRepository,
            manualRepository,
            completionRepository,
        )
    }

    private fun recommendedMission(
        createdAt: Instant,
        deletedAt: Instant? = null,
    ) = Mission(
        id = UUID.randomUUID(),
        jobId = UUID.randomUUID(),
        draftId = UUID.randomUUID(),
        guestUserId = GUEST_USER_ID,
        category = MissionCategory.MEAL,
        title = "집밥 먹기",
        description = "배달 대신 집밥을 먹습니다.",
        actionCode = "HOME_MEAL",
        metricType = MissionMetricType.COUNT,
        targetCount = 1,
        targetUnit = "TIMES_PER_WEEK",
        estimatedSavingsWon = 10_000,
        weekEndsAt = createdAt.plusSeconds(604_800),
        deletedAt = deletedAt,
        createdAt = createdAt,
    )

    private fun manualMission(createdAt: Instant) = ManualMission(
        id = UUID.randomUUID(),
        guestUserId = GUEST_USER_ID,
        category = MissionCategory.LIVING,
        missionText = "텀블러 사용하기",
        weekEndsAt = createdAt.plusSeconds(604_800),
        createdAt = createdAt,
    )

    private fun completion(
        missionId: UUID,
        source: MissionSource,
        weekStart: LocalDate,
        completedAt: String,
    ) = MissionWeeklyCompletion(
        id = UUID.randomUUID(),
        guestUserId = GUEST_USER_ID,
        missionSource = source.name,
        missionId = missionId,
        weekStartDate = weekStart,
        completedAt = Instant.parse(completedAt),
    )

    private data class Fixture(
        val service: MissionHistoryService,
        val missionRepository: MissionRepository,
        val manualRepository: ManualMissionRepository,
        val completionRepository: MissionWeeklyCompletionRepository,
    )

    companion object {
        private const val GUEST_USER_ID = 1L
        private val NOW = Instant.parse("2026-08-31T03:00:00Z")
    }
}
