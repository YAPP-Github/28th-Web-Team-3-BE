package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionGenerationOutbox
import backend.yapp.core.mission.generation.domain.MissionGenerationOutboxRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

class MissionGenerationDeliveryServiceTest {
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    @Test
    fun `expired worker lease creates the next outbox generation`() {
        val fixture = fixture(attemptCount = 1)

        assertEquals(RecoveryAction.REQUEUED, fixture.transaction.reconcile(fixture.job.id, now))
        assertEquals(MissionGenerationJobStatus.PENDING, fixture.job.status)
        val captor = ArgumentCaptor.forClass(MissionGenerationOutbox::class.java)
        verify(fixture.outboxRepository).save(captor.capture())
        assertEquals(2, captor.value.generation)
        assertEquals(fixture.job.id, captor.value.jobId)
    }

    @Test
    fun `retry exhaustion fails terminally without creating another task`() {
        val fixture = fixture(attemptCount = 5)

        assertEquals(RecoveryAction.FAILED, fixture.transaction.reconcile(fixture.job.id, now))
        assertEquals(MissionGenerationJobStatus.FAILED, fixture.job.status)
        assertEquals("MISSION_GENERATION_RETRY_EXHAUSTED", fixture.job.failureCode)
        verify(fixture.outboxRepository, never()).save(org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `one recovery failure does not prevent the next job transaction`() {
        val jobRepository = mock(MissionGenerationJobRepository::class.java)
        val transaction = mock(MissionGenerationLeaseRecoveryTransaction::class.java)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        `when`(jobRepository.findRecoverableRunningIds(now)).thenReturn(listOf(first, second))
        `when`(transaction.reconcile(first, now)).thenThrow(IllegalStateException("first failed"))
        `when`(transaction.reconcile(second, now)).thenReturn(RecoveryAction.REQUEUED)
        val service = MissionGenerationLeaseRecoveryService(
            jobRepository,
            transaction,
            java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
        )

        val result = service.reconcileExpiredLeases()

        assertEquals(1, result.requeued)
        verify(transaction, times(1)).reconcile(first, now)
        verify(transaction, times(1)).reconcile(second, now)
    }

    private fun fixture(attemptCount: Int): Fixture {
        val jobRepository = mock(MissionGenerationJobRepository::class.java)
        val outboxRepository = mock(MissionGenerationOutboxRepository::class.java)
        val job = MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = 1L,
            status = MissionGenerationJobStatus.RUNNING,
            createdAt = now.minus(Duration.ofMinutes(20)),
            attemptCount = attemptCount,
            leaseToken = UUID.randomUUID(),
            leaseExpiresAt = now,
        )
        `when`(jobRepository.findByIdForUpdate(job.id)).thenReturn(job)
        `when`(outboxRepository.findTopByJobIdOrderByGenerationDesc(job.id)).thenReturn(
            MissionGenerationOutbox(
                id = UUID.randomUUID(),
                jobId = job.id,
                generation = 1,
                nextAttemptAt = now,
                createdAt = now,
            ),
        )
        return Fixture(
            MissionGenerationLeaseRecoveryTransaction(
                jobRepository,
                outboxRepository,
            ),
            job,
            outboxRepository,
        )
    }

    private data class Fixture(
        val transaction: MissionGenerationLeaseRecoveryTransaction,
        val job: MissionGenerationJob,
        val outboxRepository: MissionGenerationOutboxRepository,
    )
}
