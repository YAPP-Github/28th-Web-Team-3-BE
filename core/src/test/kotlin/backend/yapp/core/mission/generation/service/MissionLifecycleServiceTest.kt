package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionLifecycleServiceTest {
    @Test
    fun `deletes an owned recommended mission`() {
        val fixture = fixture()
        val mission = recommendedMission()
        `when`(fixture.missionRepository.findByIdAndGuestUserId(mission.id, GUEST_USER_ID))
            .thenReturn(mission)

        fixture.service.deleteRecommended(GUEST_USER_ID, mission.id)

        verify(fixture.missionRepository).delete(mission)
    }

    @Test
    fun `does not delete a mission that is missing or owned by another user`() {
        val fixture = fixture()
        val missionId = UUID.randomUUID()
        `when`(fixture.missionRepository.findByIdAndGuestUserId(missionId, GUEST_USER_ID))
            .thenReturn(null)

        val exception = assertFailsWith<BaseException> {
            fixture.service.deleteRecommended(GUEST_USER_ID, missionId)
        }

        assertEquals(ErrorCode.MISSION_NOT_FOUND, exception.errorCode)
        verify(fixture.missionRepository, never()).delete(org.mockito.ArgumentMatchers.any(Mission::class.java))
    }

    @Test
    fun `deletes an incomplete recommended mission`() {
        val fixture = fixture()
        val mission = recommendedMission().also { it.markIncomplete() }
        `when`(fixture.missionRepository.findByIdAndGuestUserId(mission.id, GUEST_USER_ID))
            .thenReturn(mission)

        fixture.service.deleteRecommended(GUEST_USER_ID, mission.id)

        verify(fixture.missionRepository).delete(mission)
    }

    private fun fixture(): Fixture {
        val missionRepository = mock(MissionRepository::class.java)
        val service = MissionLifecycleService(
            missionRepository = missionRepository,
            manualRepository = mock(ManualMissionRepository::class.java),
            outcomeRepository = mock(MissionOutcomeEventRepository::class.java),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
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
        estimatedSavingsWon = 15000,
        status = MissionStatus.ACTIVE,
        weekEndsAt = NOW.plusSeconds(604800),
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
