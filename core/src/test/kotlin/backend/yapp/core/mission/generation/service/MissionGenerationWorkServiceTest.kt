package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionBlogTip
import backend.yapp.core.mission.generation.domain.MissionBlogTipRepository
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionDraftTemplate
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionBlogSearchResult
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationWorkServiceTest {
    @Test
    fun `completes generation without writing blog tips when search results are empty`() {
        val jobs = mock(MissionGenerationJobRepository::class.java)
        val profiles = mock(OnboardingProfileRepository::class.java)
        val templates = mock(MissionDraftTemplateRepository::class.java)
        val drafts = mock(MissionDraftRepository::class.java)
        val blogTips = mock(MissionBlogTipRepository::class.java)
        val clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC)
        val service = MissionGenerationWorkService(jobs, profiles, templates, drafts, blogTips, clock)
        val work = MissionGenerationWork(
            jobId = UUID.randomUUID(),
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 10_000,
            birthDate = java.time.LocalDate.of(2000, 1, 1),
            address = null,
            leaseToken = UUID.randomUUID(),
        )
        val job = mock(MissionGenerationJob::class.java)
        val template = mock(MissionDraftTemplate::class.java)
        `when`(jobs.findByIdForUpdate(work.jobId)).thenReturn(job)
        `when`(job.ownsLease(work.leaseToken, clock.instant())).thenReturn(true)
        `when`(templates.findByTargetCodeAndActiveTrue(work.item.name)).thenReturn(template)
        `when`(template.id).thenReturn(1L)

        service.complete(
            work,
            MissionAlternativeGenerationResult(
                listOf(MissionAlternativeTemplate("{count}회 대체하기", "설명")),
                MissionDraftGenerationSource.AI,
            ),
            emptyList(),
        )

        verifyNoInteractions(blogTips)
    }

    @Test
    fun `persists newly searched blog tips and updates an existing URL`() {
        val jobs = mock(MissionGenerationJobRepository::class.java)
        val profiles = mock(OnboardingProfileRepository::class.java)
        val templates = mock(MissionDraftTemplateRepository::class.java)
        val drafts = mock(MissionDraftRepository::class.java)
        val blogTips = mock(MissionBlogTipRepository::class.java)
        val clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC)
        val service = MissionGenerationWorkService(jobs, profiles, templates, drafts, blogTips, clock)
        val work = MissionGenerationWork(
            jobId = UUID.randomUUID(),
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 10_000,
            birthDate = java.time.LocalDate.of(2000, 1, 1),
            address = null,
            leaseToken = UUID.randomUUID(),
        )
        val job = mock(MissionGenerationJob::class.java)
        val template = mock(MissionDraftTemplate::class.java)
        `when`(jobs.findByIdForUpdate(work.jobId)).thenReturn(job)
        `when`(job.ownsLease(work.leaseToken, clock.instant())).thenReturn(true)
        `when`(templates.findByTargetCodeAndActiveTrue(work.item.name)).thenReturn(template)
        `when`(template.id).thenReturn(1L)
        val existing = MissionBlogTip(
            UUID.randomUUID(),
            work.guestUserId,
            work.item,
            "old title",
            "old source",
            "https://blog.example.test/existing",
            Instant.EPOCH,
        )
        `when`(blogTips.findByGuestUserIdAndUrl(work.guestUserId, "https://blog.example.test/new")).thenReturn(null)
        `when`(blogTips.findByGuestUserIdAndUrl(work.guestUserId, existing.url)).thenReturn(existing)

        service.complete(
            work,
            MissionAlternativeGenerationResult(
                listOf(MissionAlternativeTemplate("{count}회 대체하기", "설명")),
                MissionDraftGenerationSource.AI,
            ),
            listOf(
                MissionBlogSearchResult("new title", "설명", "source", "https://blog.example.test/new"),
                MissionBlogSearchResult("updated title", "설명", "updated source", existing.url),
            ),
        )

        val saved = ArgumentCaptor.forClass(MissionBlogTip::class.java)
        verify(blogTips).save(saved.capture())
        assertEquals("https://blog.example.test/new", saved.value.url)
        assertEquals("updated title", existing.title)
        assertEquals("updated source", existing.source)
        assertEquals(clock.instant(), existing.searchedAt)
        verify(blogTips, never()).save(existing)
    }
}
