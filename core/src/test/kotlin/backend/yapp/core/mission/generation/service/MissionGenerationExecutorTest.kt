package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationExecutorTest {
    @Test
    fun `failed content generation does not complete drafts or mark exposure shown`() {
        val workService = mock(MissionGenerationWorkService::class.java)
        val generator = mock(MissionDraftContentGenerator::class.java)
        val jobId = UUID.randomUUID()
        val work = MissionGenerationWork(
            jobId,
            1,
            listOf(
                MissionDraftCandidate(
                    templateId = 1,
                    category = MissionCategory.MEAL,
                    templateTitle = "제목",
                    templateDescription = "설명",
                    actionCode = "ACTION",
                    metricType = MissionMetricType.COUNT,
                    targetCount = 1,
                    targetUnit = "TIMES_PER_WEEK",
                    estimatedSavingsWon = 1000,
                ),
            ),
        )
        `when`(workService.prepare(jobId)).thenReturn(MissionGenerationPreparation.Claimed(work))
        `when`(
            generator.generate(
                MissionDraftContentRequest(
                    jobId = jobId,
                    guestUserId = 1,
                    candidates = work.candidates,
                ),
            ),
        ).thenThrow(IllegalStateException("provider failed"))

        assertFailsWith<IllegalStateException> {
            MissionGenerationExecutor(workService, generator).execute(jobId)
        }

        verify(workService).prepare(jobId)
        verifyNoMoreInteractions(workService)
    }
}
