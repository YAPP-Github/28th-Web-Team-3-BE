package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionGenerationOutboxRepository
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentCaptor
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
            category = MissionCategory.MEAL,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 1,
            createdAt = now,
        ).also { it.start(now) }
        `when`(fixture.onboardingRepository.findByGuestUserIdForUpdate(GUEST_USER_ID))
            .thenReturn(
                OnboardingProfile(
                    GUEST_USER_ID,
                    birthDate = LocalDate.of(2000, 1, 1),
                    address = ResidentialArea.SEOUL,
                    status = OnboardingStatus.COMPLETED,
                ),
            )
        `when`(
            fixture.jobRepository.findFirstByGuestUserIdAndActiveGenerationKeyOrderByCreatedAtDesc(
                GUEST_USER_ID,
                MissionGenerationJob.ACTIVE_KEY,
            ),
        ).thenReturn(existing)

        val result = fixture.service.request(
            GUEST_USER_ID,
            MissionCategory.MEAL,
            MissionItem.DELIVERY_FOOD,
            1,
            1,
        )

        assertEquals(existing.id, result.jobId)
        assertEquals(MissionGenerationJobStatus.RUNNING, result.status)
        verify(fixture.jobRepository, never()).saveAndFlush(
            org.mockito.ArgumentMatchers.any(MissionGenerationJob::class.java),
        )
        verify(fixture.outboxRepository, never()).save(
            org.mockito.ArgumentMatchers.any(),
        )
        verify(fixture.eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `new generation publishes one outbox-created event for after-commit delivery`() {
        val fixture = fixture()
        `when`(fixture.onboardingRepository.findByGuestUserIdForUpdate(GUEST_USER_ID))
            .thenReturn(
                OnboardingProfile(
                    GUEST_USER_ID,
                    birthDate = LocalDate.of(2000, 1, 1),
                    address = ResidentialArea.SEOUL,
                    status = OnboardingStatus.COMPLETED,
                ),
            )
        `when`(fixture.jobRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MissionGenerationJob::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] }
        `when`(fixture.outboxRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer { invocation -> invocation.arguments[0] }

        fixture.service.request(
            GUEST_USER_ID,
            MissionCategory.MEAL,
            MissionItem.DELIVERY_FOOD,
            1,
            1,
        )

        val outbox = ArgumentCaptor.forClass(backend.yapp.core.mission.generation.domain.MissionGenerationOutbox::class.java)
        verify(fixture.outboxRepository).save(outbox.capture())
        val event = ArgumentCaptor.forClass(MissionGenerationOutboxCreatedEvent::class.java)
        verify(fixture.eventPublisher).publishEvent(event.capture())
        assertEquals(outbox.value.id, event.value.outboxId)
    }

    private fun fixture(): Fixture {
        val jobRepository = mock(MissionGenerationJobRepository::class.java)
        val onboardingRepository = mock(OnboardingProfileRepository::class.java)
        val draftRepository = mock(MissionDraftRepository::class.java)
        val missionRepository = mock(MissionRepository::class.java)
        val outboxRepository = mock(MissionGenerationOutboxRepository::class.java)
        val eventPublisher = mock(ApplicationEventPublisher::class.java)
        val service = MissionGenerationService(
            jobRepository = jobRepository,
            draftRepository = draftRepository,
            missionRepository = missionRepository,
            onboardingProfileRepository = onboardingRepository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            outboxRepository = outboxRepository,
            eventPublisher = eventPublisher,
        )
        return Fixture(
            service,
            jobRepository,
            onboardingRepository,
            outboxRepository,
            eventPublisher,
        )
    }

    private data class Fixture(
        val service: MissionGenerationService,
        val jobRepository: MissionGenerationJobRepository,
        val onboardingRepository: OnboardingProfileRepository,
        val outboxRepository: MissionGenerationOutboxRepository,
        val eventPublisher: ApplicationEventPublisher,
    )

    companion object {
        private const val GUEST_USER_ID = 1L
    }
}
