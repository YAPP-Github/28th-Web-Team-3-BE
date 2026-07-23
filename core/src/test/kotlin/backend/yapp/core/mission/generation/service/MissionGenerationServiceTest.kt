package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.survey.domain.MissionSurvey
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher

class MissionGenerationServiceTest {
    private val now = Instant.parse("2026-07-23T00:00:00Z")

    @Test
    fun `request reuses an active generation job`() {
        val fixture = fixture()
        val existing = MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = GUEST_USER_ID,
            createdAt = now,
        ).also { it.start(now) }
        `when`(fixture.onboardingRepository.findByGuestUserIdForUpdate(GUEST_USER_ID))
            .thenReturn(OnboardingProfile(GUEST_USER_ID, status = OnboardingStatus.COMPLETED))
        `when`(fixture.surveyRepository.findByGuestUserId(GUEST_USER_ID))
            .thenReturn(MissionSurvey(GUEST_USER_ID))
        `when`(
            fixture.jobRepository.findFirstByGuestUserIdAndActiveGenerationKeyOrderByCreatedAtDesc(
                GUEST_USER_ID,
                MissionGenerationJob.ACTIVE_KEY,
            ),
        ).thenReturn(existing)

        val result = fixture.service.request(GUEST_USER_ID)

        assertEquals(existing.id, result.jobId)
        assertEquals(MissionGenerationJobStatus.RUNNING, result.status)
        verify(fixture.jobRepository, never()).saveAndFlush(
            org.mockito.ArgumentMatchers.any(MissionGenerationJob::class.java),
        )
        verify(fixture.eventPublisher, never()).publishEvent(
            org.mockito.ArgumentMatchers.any(MissionGenerationRequestedEvent::class.java),
        )
    }

    private fun fixture(): Fixture {
        val jobRepository = mock(MissionGenerationJobRepository::class.java)
        val onboardingRepository = mock(OnboardingProfileRepository::class.java)
        val surveyRepository = mock(MissionSurveyRepository::class.java)
        val draftRepository = mock(MissionDraftRepository::class.java)
        val missionRepository = mock(MissionRepository::class.java)
        val eventPublisher = mock(ApplicationEventPublisher::class.java)
        val service = MissionGenerationService(
            jobRepository = jobRepository,
            draftRepository = draftRepository,
            missionRepository = missionRepository,
            onboardingProfileRepository = onboardingRepository,
            surveyRepository = surveyRepository,
            eventPublisher = eventPublisher,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Fixture(
            service,
            jobRepository,
            onboardingRepository,
            surveyRepository,
            eventPublisher,
        )
    }

    private data class Fixture(
        val service: MissionGenerationService,
        val jobRepository: MissionGenerationJobRepository,
        val onboardingRepository: OnboardingProfileRepository,
        val surveyRepository: MissionSurveyRepository,
        val eventPublisher: ApplicationEventPublisher,
    )

    companion object {
        private const val GUEST_USER_ID = 1L
    }
}
