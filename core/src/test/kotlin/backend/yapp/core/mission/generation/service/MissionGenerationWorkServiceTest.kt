package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionDraftTemplate
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationWorkServiceTest {
    @Test
    fun `completes generation without writing legacy blog tips`() {
        val jobs = mock(MissionGenerationJobRepository::class.java)
        val profiles = mock(OnboardingProfileRepository::class.java)
        val templates = mock(MissionDraftTemplateRepository::class.java)
        val drafts = mock(MissionDraftRepository::class.java)
        val clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC)
        val service = MissionGenerationWorkService(jobs, profiles, templates, drafts, clock)
        val work = MissionGenerationWork(
            jobId = UUID.randomUUID(),
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 10_000,
            birthDate = LocalDate.of(2000, 1, 1),
            address = null,
            leaseToken = UUID.randomUUID(),
        )
        val job = mock(MissionGenerationJob::class.java)
        val template = mock(MissionDraftTemplate::class.java)
        `when`(jobs.findByIdForUpdate(work.jobId)).thenReturn(job)
        `when`(job.ownsLease(work.leaseToken, clock.instant())).thenReturn(true)
        `when`(templates.findByTargetCodeAndActiveTrue(work.item.name)).thenReturn(template)
        `when`(template.id).thenReturn(1L)
        val generated = MissionAlternativeGenerationResult(
            listOf(MissionAlternativeTemplate("{count}회 대체하기", "설명")),
            MissionDraftGenerationSource.AI,
        )

        service.complete(work, generated)

        verify(drafts).saveAll(org.mockito.ArgumentMatchers.anyList())
        verify(job).succeed(clock.instant(), clock.instant().plus(java.time.Duration.ofHours(24)), generated.source)
    }
}
