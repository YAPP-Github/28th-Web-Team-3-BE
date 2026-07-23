package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTrace
import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTraceRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshot
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshotRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DatabaseMissionRecommendationTraceTest {
    @Test
    fun `links selected run first and marks only persisted drafts as shown after completion`() {
        val snapshotRepository = mock(MissionRecommendationSnapshotRepository::class.java)
        val candidateRepository = mock(MissionRecommendationCandidateTraceRepository::class.java)
        val snapshot = MissionRecommendationSnapshot(
            UUID.randomUUID(),
            1,
            algorithmVersion = "v1",
            semanticProvider = "openai",
            semanticModelVersion = "embedding-v1",
            eligibleCandidateIds = "1,2",
            retrievedCandidateIds = "1",
            weeklyContextSnapshot = "plan=PLAN_1",
            createdAt = Instant.EPOCH,
        )
        val first = trace(snapshot.id, 1)
        val second = trace(snapshot.id, 2)
        val service = DatabaseMissionRecommendationTrace(
            snapshotRepository,
            candidateRepository,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
        val jobId = UUID.randomUUID()
        `when`(snapshotRepository.findFirstByGuestUserIdAndJobIdIsNullOrderByCreatedAtDesc(1))
            .thenReturn(snapshot)
        `when`(snapshotRepository.findByJobId(jobId)).thenReturn(snapshot)
        `when`(candidateRepository.findAllBySnapshotId(snapshot.id)).thenReturn(listOf(first, second))

        service.linkToJob(1, jobId)
        assertEquals(jobId, snapshot.jobId)
        assertFalse(first.shown)
        assertFalse(second.shown)

        service.markShown(jobId, setOf(1))

        assertTrue(first.shown)
        assertFalse(second.shown)
    }

    private fun trace(snapshotId: UUID, templateId: Long) = MissionRecommendationCandidateTrace(
        id = UUID.randomUUID(),
        snapshotId = snapshotId,
        templateId = templateId,
        rankPosition = templateId.toInt(),
        rawScore = 1.0,
        adjustedScore = 1.0,
        retrieved = true,
        appliedPenalties = "",
    )
}
