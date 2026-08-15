package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import backend.yapp.core.mission.generation.port.MissionBlogSearchResult
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationExecutorTest {
    @Test
    fun `successful blog results cross the executor boundary into generation and persistence`() {
        val workService = mock(MissionGenerationWorkService::class.java)
        val searchPort = mock(MissionBlogSearchPort::class.java)
        val generator = mock(MissionAlternativeGenerationPort::class.java)
        val jobId = UUID.randomUUID()
        val work = MissionGenerationWork(
            jobId = jobId,
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 3,
            baselineAmountWon = 30_000,
            birthDate = LocalDate.of(2000, 1, 1),
            address = ResidentialArea.SEOUL,
            leaseToken = UUID.randomUUID(),
        )
        val clock = Clock.fixed(java.time.Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val query = MissionSearchQueryFactory.create(
            work.item,
            work.birthDate,
            work.address,
            LocalDate.now(clock),
            work.jobId.hashCode(),
        )
        val blogResults = listOf(
            MissionBlogSearchResult(
                title = "절약 팁",
                description = "설명",
                source = "작성자",
                url = "https://blog.example.test/1",
            ),
        )
        val generated = MissionAlternativeGenerationResult(
            alternatives = listOf(MissionAlternativeTemplate("{count}회 절약하기", "설명")),
            source = MissionDraftGenerationSource.AI,
        )
        `when`(workService.prepare(jobId)).thenReturn(MissionGenerationPreparation.Claimed(work))
        `when`(workService.clock).thenReturn(clock)
        `when`(searchPort.search(query, 15)).thenReturn(
            MissionBlogSearchOutcome.Completed(
                MissionBlogSearchOutcomeCategory.SUCCESS,
                providerItemCount = 1,
                results = blogResults,
            ),
        )
        `when`(generator.generate(MissionAlternativeGenerationRequest(work.item, blogResults))).thenReturn(generated)

        MissionGenerationExecutor(workService, searchPort, generator, 15).execute(jobId)

        verify(searchPort).search(query, 15)
        verify(generator).generate(MissionAlternativeGenerationRequest(work.item, blogResults))
        verify(workService).complete(work, generated, blogResults)
    }

    @Test
    fun `AI generation failure releases the claimed job for retry`() {
        val workService = mock(MissionGenerationWorkService::class.java)
        val searchPort = mock(MissionBlogSearchPort::class.java)
        val generator = mock(MissionAlternativeGenerationPort::class.java)
        val jobId = UUID.randomUUID()
        val work = MissionGenerationWork(
            jobId = jobId,
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 3,
            baselineAmountWon = 30_000,
            birthDate = LocalDate.of(2000, 1, 1),
            address = ResidentialArea.SEOUL,
            leaseToken = UUID.randomUUID(),
        )
        val clock = Clock.fixed(java.time.Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val query = MissionSearchQueryFactory.create(
            work.item,
            work.birthDate,
            work.address,
            LocalDate.now(clock),
            work.jobId.hashCode(),
        )
        `when`(workService.prepare(jobId)).thenReturn(MissionGenerationPreparation.Claimed(work))
        `when`(workService.clock).thenReturn(clock)
        `when`(searchPort.search(query, 15)).thenReturn(
            MissionBlogSearchOutcome.Completed(
                MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT,
                providerItemCount = 0,
                results = emptyList(),
            ),
        )
        `when`(generator.generate(MissionAlternativeGenerationRequest(work.item, emptyList())))
            .thenThrow(IllegalStateException("provider failed"))

        assertFailsWith<IllegalStateException> {
            MissionGenerationExecutor(workService, searchPort, generator, 15).execute(jobId)
        }

        verify(workService).prepare(jobId)
        verify(workService).releaseOrFail(work)
    }

    @Test
    fun `blog search failure falls back to empty AI context without failing the job`() {
        val workService = mock(MissionGenerationWorkService::class.java)
        val searchPort = mock(MissionBlogSearchPort::class.java)
        val generator = mock(MissionAlternativeGenerationPort::class.java)
        val jobId = UUID.randomUUID()
        val work = MissionGenerationWork(
            jobId = jobId,
            guestUserId = 1,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 3,
            baselineAmountWon = 30_000,
            birthDate = LocalDate.of(2000, 1, 1),
            address = ResidentialArea.SEOUL,
            leaseToken = UUID.randomUUID(),
        )
        val clock = Clock.fixed(java.time.Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val query = MissionSearchQueryFactory.create(
            work.item,
            work.birthDate,
            work.address,
            LocalDate.now(clock),
            work.jobId.hashCode(),
        )
        `when`(workService.prepare(jobId)).thenReturn(MissionGenerationPreparation.Claimed(work))
        `when`(workService.clock).thenReturn(clock)
        `when`(searchPort.search(query, 15)).thenReturn(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.AUTHORIZATION, attempts = 1),
        )
        val generated = backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult(
            alternatives = listOf(
                backend.yapp.core.mission.generation.port.MissionAlternativeTemplate("{count}회 절약하기", "설명"),
            ),
            source = backend.yapp.core.mission.generation.port.MissionDraftGenerationSource.AI,
        )
        `when`(generator.generate(MissionAlternativeGenerationRequest(work.item, emptyList()))).thenReturn(generated)

        MissionGenerationExecutor(workService, searchPort, generator, 15).execute(jobId)

        verify(workService).complete(work, generated, emptyList())
    }
}
