package backend.yapp.core.mission.generation.domain

import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissionGenerationJobTest {
    private val now = Instant.parse("2026-07-23T00:00:00Z")

    @Test
    fun `job follows pending running succeeded transition and releases active key`() {
        val job = job()

        assertTrue(job.start(now.plusSeconds(1)))
        job.succeed(
            now.plusSeconds(2),
            now.plusSeconds(3_600),
            MissionDraftGenerationSource.MOCK,
        )

        assertEquals(MissionGenerationJobStatus.SUCCEEDED, job.status)
        assertEquals(null, job.activeGenerationKey)
        assertEquals(MissionDraftGenerationSource.MOCK, job.generationSource)
        assertFalse(job.isExpired(now.plusSeconds(3_599)))
        assertTrue(job.isExpired(now.plusSeconds(3_600)))
    }

    @Test
    fun `confirmation is idempotent only for the same fingerprint`() {
        val job = job().also {
            it.start(now)
            it.succeed(now, now.plusSeconds(3_600), MissionDraftGenerationSource.MOCK)
        }

        assertEquals(ConfirmationResult.CREATED, job.confirm("same", now))
        assertEquals(ConfirmationResult.IDEMPOTENT, job.confirm("same", now.plusSeconds(1)))
        assertEquals(ConfirmationResult.CONFLICT, job.confirm("different", now.plusSeconds(2)))
    }

    @Test
    fun `failed job releases active key and ignores repeated terminal failure`() {
        val job = job()

        job.fail("FIRST", now.plusSeconds(1))
        job.fail("SECOND", now.plusSeconds(2))

        assertEquals(MissionGenerationJobStatus.FAILED, job.status)
        assertEquals("FIRST", job.failureCode)
        assertEquals(null, job.activeGenerationKey)
    }

    private fun job(): MissionGenerationJob =
        MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = 1,
            createdAt = now,
        )
}
