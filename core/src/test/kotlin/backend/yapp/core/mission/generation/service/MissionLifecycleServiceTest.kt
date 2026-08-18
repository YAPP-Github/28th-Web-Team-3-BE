package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletionRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MissionLifecycleServiceTest {
    @Test
    fun `user deletion soft deletes an owned mission and preserves its row`() {
        val fixture = fixture()
        val mission = recommendedMission()
        `when`(fixture.missionRepository.findByIdAndGuestUserIdForUpdate(mission.id, GUEST_USER_ID)).thenReturn(mission)

        fixture.service.delete(GUEST_USER_ID, MissionSource.RECOMMENDED, mission.id)

        assertNotNull(mission.deletedAt)
    }

    @Test
    fun `cannot delete a missing or another users mission`() {
        val fixture = fixture()
        val missionId = UUID.randomUUID()
        `when`(fixture.missionRepository.findByIdAndGuestUserIdForUpdate(missionId, GUEST_USER_ID)).thenReturn(null)

        val exception = assertFailsWith<BaseException> {
            fixture.service.delete(GUEST_USER_ID, MissionSource.RECOMMENDED, missionId)
        }

        assertEquals(ErrorCode.MISSION_NOT_FOUND, exception.errorCode)
    }

    private fun fixture(): Fixture {
        val missionRepository = mock(MissionRepository::class.java)
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val service = MissionLifecycleService(
            missionRepository = missionRepository,
            manualRepository = mock(ManualMissionRepository::class.java),
            weeklyCompletionRepository = mock(MissionWeeklyCompletionRepository::class.java),
            clock = clock,
            weekCalendar = MissionWeekCalendar(clock),
        )
        return Fixture(service, missionRepository)
    }

    private fun recommendedMission() = Mission(
        id = UUID.randomUUID(),
        jobId = UUID.randomUUID(),
        draftId = UUID.randomUUID(),
        guestUserId = GUEST_USER_ID,
        category = MissionCategory.MEAL,
        title = "집밥 먹기",
        description = "배달 대신 집밥을 먹습니다.",
        actionCode = "REPLACE_DELIVERY_WITH_HOME_MEAL",
        metricType = MissionMetricType.COUNT,
        targetCount = 1,
        targetUnit = "TIMES_PER_WEEK",
        estimatedSavingsWon = 15_000,
        weekEndsAt = NOW.plusSeconds(604_800),
        createdAt = NOW,
    )

    private data class Fixture(
        val service: MissionLifecycleService,
        val missionRepository: MissionRepository,
    )

    companion object {
        private const val GUEST_USER_ID = 1L
        private val NOW = Instant.parse("2026-07-24T00:00:00Z")
    }
}
