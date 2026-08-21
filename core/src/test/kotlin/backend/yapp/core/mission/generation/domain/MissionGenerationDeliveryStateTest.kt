package backend.yapp.core.mission.generation.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionGenerationDeliveryStateTest {
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    @Test
    fun `active lease prevents duplicate claim and expired lease can be reclaimed`() {
        val job = MissionGenerationJob(UUID.randomUUID(), 1L, createdAt = now)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        assertTrue(job.claim(now, first, Duration.ofMinutes(10)))
        assertFalse(job.claim(now.plusSeconds(1), second, Duration.ofMinutes(10)))
        assertTrue(job.claim(now.plus(Duration.ofMinutes(10)), second, Duration.ofMinutes(10)))
        assertEquals(2, job.attemptCount)
        assertEquals(second, job.leaseToken)
        assertEquals(now, job.workerStartedAt)
    }

    @Test
    fun `fifth expired attempt becomes terminal failure with client retry code`() {
        val job = MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = 1L,
            status = MissionGenerationJobStatus.RUNNING,
            createdAt = now,
            attemptCount = 5,
            leaseToken = UUID.randomUUID(),
            leaseExpiresAt = now,
        )

        assertFalse(job.retryOrFail(now, 5))
        assertEquals(MissionGenerationJobStatus.FAILED, job.status)
        assertEquals("MISSION_GENERATION_RETRY_EXHAUSTED", job.failureCode)
        assertNull(job.activeGenerationKey)
        assertEquals(now, job.completedAt)
    }

    @Test
    fun `execution failure releases owned lease for an immediate retry`() {
        val job = MissionGenerationJob(UUID.randomUUID(), 1L, createdAt = now)
        val token = UUID.randomUUID()
        assertTrue(job.claim(now, token, Duration.ofMinutes(10)))

        assertTrue(job.releaseOrFail(token, now.plusSeconds(1), 5))

        assertEquals(MissionGenerationJobStatus.PENDING, job.status)
        assertNull(job.leaseToken)
        assertNull(job.leaseExpiresAt)
    }

    @Test
    fun `retry does not overwrite the first worker start timestamp`() {
        val job = MissionGenerationJob(UUID.randomUUID(), 1L, createdAt = now)
        val firstToken = UUID.randomUUID()
        val secondToken = UUID.randomUUID()

        assertTrue(job.claim(now, firstToken, Duration.ofMinutes(10)))
        assertTrue(job.releaseOrFail(firstToken, now.plusSeconds(1), 5))
        assertTrue(job.claim(now.plusSeconds(2), secondToken, Duration.ofMinutes(10)))

        assertEquals(now, job.workerStartedAt)
    }

    @Test
    fun `execution failure on final attempt becomes terminal`() {
        val token = UUID.randomUUID()
        val job = MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = 1L,
            status = MissionGenerationJobStatus.RUNNING,
            createdAt = now,
            attemptCount = 5,
            leaseToken = token,
            leaseExpiresAt = now.plusSeconds(60),
        )

        assertTrue(job.releaseOrFail(token, now, 5))

        assertEquals(MissionGenerationJobStatus.FAILED, job.status)
        assertEquals("MISSION_GENERATION_RETRY_EXHAUSTED", job.failureCode)
        assertEquals(now, job.completedAt)
    }

    @Test
    fun `claimed outbox is retried after timeout and records one published task name`() {
        val outbox = MissionGenerationOutbox(UUID.randomUUID(), UUID.randomUUID(), nextAttemptAt = now, createdAt = now)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        assertTrue(outbox.claim(now, Duration.ofMinutes(2), first))
        assertFalse(outbox.claim(now.plusSeconds(30), Duration.ofMinutes(2), second))
        assertTrue(outbox.claim(now.plus(Duration.ofMinutes(2)), Duration.ofMinutes(2), second))
        assertFalse(outbox.published(first, "stale-task", now.plusSeconds(120)))
        assertTrue(
            outbox.published(second, "projects/p/locations/l/queues/q/tasks/mission", now.plusSeconds(121)),
        )

        assertEquals(MissionGenerationOutboxStatus.PUBLISHED, outbox.status)
        assertEquals("projects/p/locations/l/queues/q/tasks/mission", outbox.taskName)
    }
}
