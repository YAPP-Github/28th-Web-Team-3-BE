package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.mission.generation.port.MissionKnowledge
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalResult
import backend.yapp.core.mission.generation.port.MissionKnowledgeSelectionPolicy
import backend.yapp.core.mission.generation.port.MissionKnowledgeTrace
import backend.yapp.core.mission.generation.port.MissionKnowledgeTracePort
import backend.yapp.core.mission.generation.port.MissionKnowledgeVerificationPort
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationExecutorTest {
    @Test
    fun `retrieved and verified knowledge crosses the executor boundary into generation`() {
        val fixture = fixture()
        val knowledge = listOf(MissionKnowledge(1, "할인 혜택", "DISCOUNT", null, null, null))
        val retrieval = MissionKnowledgeRetrievalResult(knowledge, 1)
        `when`(fixture.retriever.retrieve(fixture.request)).thenReturn(retrieval)
        `when`(fixture.verifier.verify(knowledge)).thenReturn(knowledge)
        val generationRequest = MissionAlternativeGenerationRequest(
            fixture.work.item,
            knowledge,
            fixture.personalizationContext,
        )
        `when`(fixture.generator.generate(generationRequest))
            .thenReturn(fixture.generated)

        executor(fixture)
            .execute(fixture.work.jobId)

        verify(fixture.traceRecorder).record(
            MissionKnowledgeTrace(
                fixture.work.jobId,
                fixture.work.item,
                1,
                1,
                listOf(1),
                MissionKnowledgeSelectionPolicy.ALL,
            ),
        )
        verify(fixture.generator).generate(generationRequest)
        verify(fixture.workService).complete(fixture.work, fixture.generated)
    }

    @Test
    fun `all candidates are verified before one knowledge is selected`() {
        val fixture = fixture()
        val candidates = (1L..6L).map { id ->
            MissionKnowledge(id, "지식 $id", null, null, null, null)
        }
        val selected = MissionKnowledgeSelector.select(fixture.work.jobId, candidates).knowledge
        `when`(fixture.retriever.retrieve(fixture.request)).thenReturn(
            MissionKnowledgeRetrievalResult(candidates, candidates.size),
        )
        `when`(fixture.verifier.verify(candidates)).thenReturn(candidates)
        val generationRequest = MissionAlternativeGenerationRequest(
            fixture.work.item,
            selected,
            fixture.personalizationContext,
        )
        `when`(fixture.generator.generate(generationRequest)).thenReturn(fixture.generated)

        executor(fixture).execute(fixture.work.jobId)

        verify(fixture.verifier).verify(candidates)
        verify(fixture.generator).generate(generationRequest)
        verify(fixture.traceRecorder).record(
            MissionKnowledgeTrace(
                fixture.work.jobId,
                fixture.work.item,
                6,
                6,
                selected.map { it.id },
                MissionKnowledgeSelectionPolicy.DETERMINISTIC_RANDOM_1,
            ),
        )
    }

    @Test
    fun `retrieval failure uses empty knowledge and still completes`() {
        val fixture = fixture()
        `when`(fixture.retriever.retrieve(fixture.request)).thenThrow(IllegalStateException("db failed"))
        `when`(fixture.verifier.verify(emptyList())).thenReturn(emptyList())
        val generationRequest = MissionAlternativeGenerationRequest(
            fixture.work.item,
            emptyList(),
            fixture.personalizationContext,
        )
        `when`(fixture.generator.generate(generationRequest))
            .thenReturn(fixture.generated)

        executor(fixture)
            .execute(fixture.work.jobId)

        verify(fixture.workService).complete(fixture.work, fixture.generated)
    }

    @Test
    fun `generation failure releases the claimed job for retry`() {
        val fixture = fixture()
        `when`(fixture.retriever.retrieve(fixture.request)).thenReturn(
            MissionKnowledgeRetrievalResult(emptyList(), 0),
        )
        `when`(fixture.verifier.verify(emptyList())).thenReturn(emptyList())
        val generationRequest = MissionAlternativeGenerationRequest(
            fixture.work.item,
            emptyList(),
            fixture.personalizationContext,
        )
        `when`(fixture.generator.generate(generationRequest))
            .thenThrow(IllegalStateException("provider failed"))

        assertFailsWith<IllegalStateException> {
            executor(fixture).execute(fixture.work.jobId)
        }

        verify(fixture.workService).releaseOrFail(fixture.work)
    }

    private fun fixture(): Fixture {
        val workService = mock(MissionGenerationWorkService::class.java)
        val retriever = mock(MissionKnowledgeRetrievalPort::class.java)
        val verifier = mock(MissionKnowledgeVerificationPort::class.java)
        val traceRecorder = mock(MissionKnowledgeTracePort::class.java)
        val generator = mock(MissionAlternativeGenerationPort::class.java)
        val clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        val work = MissionGenerationWork(
            jobId = UUID.randomUUID(),
            guestUserId = 1,
            item = MissionItem.CONVENIENCE_STORE,
            baselineFrequency = 3,
            baselineAmountWon = 30_000,
            birthDate = LocalDate.of(2000, 1, 1),
            address = ResidentialArea.SEOUL,
            leaseToken = UUID.randomUUID(),
        )
        `when`(workService.prepare(work.jobId)).thenReturn(MissionGenerationPreparation.Claimed(work))
        `when`(workService.clock).thenReturn(clock)
        val personalizationContext = MissionSearchQueryFactory.create(
            work.item,
            work.birthDate,
            work.address,
            LocalDate.now(clock),
            work.baselineFrequency,
            work.baselineAmountWon,
        )
        val request = MissionKnowledgeRetrievalRequest(work.item, LocalDate.now(clock))
        val generated = MissionAlternativeGenerationResult(
            listOf(MissionAlternativeTemplate("{count}회 절약하기", "설명")),
            MissionDraftGenerationSource.AI,
        )
        return Fixture(
            workService,
            retriever,
            verifier,
            traceRecorder,
            generator,
            work,
            request,
            personalizationContext,
            generated,
        )
    }

    private fun executor(fixture: Fixture): MissionGenerationExecutor = MissionGenerationExecutor(
        fixture.workService,
        fixture.retriever,
        fixture.verifier,
        fixture.traceRecorder,
        fixture.generator,
    )

    private data class Fixture(
        val workService: MissionGenerationWorkService,
        val retriever: MissionKnowledgeRetrievalPort,
        val verifier: MissionKnowledgeVerificationPort,
        val traceRecorder: MissionKnowledgeTracePort,
        val generator: MissionAlternativeGenerationPort,
        val work: MissionGenerationWork,
        val request: MissionKnowledgeRetrievalRequest,
        val personalizationContext: String,
        val generated: MissionAlternativeGenerationResult,
    )
}
