package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
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
        `when`(searchPort.search(query, 15)).thenReturn(emptyList())
        `when`(generator.generate(MissionAlternativeGenerationRequest(work.item, emptyList())))
            .thenThrow(IllegalStateException("provider failed"))

        assertFailsWith<IllegalStateException> {
            MissionGenerationExecutor(workService, searchPort, generator, 15).execute(jobId)
        }

        verify(workService).prepare(jobId)
        verify(workService).releaseOrFail(work)
    }
}
