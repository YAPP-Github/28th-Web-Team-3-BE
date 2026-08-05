package backend.yapp.api.mission.generation

import backend.yapp.api.mission.generation.controller.MissionGenerationWorkerController
import backend.yapp.core.mission.generation.service.MissionGenerationExecutionResult
import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MissionGenerationInternalControllerTest {
    @Test
    fun `active lease returns retryable status to Cloud Tasks`() {
        val executor = mock(MissionGenerationExecutor::class.java)
        val jobId = UUID.randomUUID()
        `when`(executor.execute(jobId)).thenReturn(MissionGenerationExecutionResult.ACTIVE_LEASE)

        val response = MissionGenerationWorkerController(executor).execute(jobId)

        assertEquals(503, response.statusCode.value())
    }

    @Test
    fun `completed execution is acknowledged only after executor returns`() {
        val executor = mock(MissionGenerationExecutor::class.java)
        val jobId = UUID.randomUUID()
        `when`(executor.execute(jobId)).thenReturn(MissionGenerationExecutionResult.COMPLETED)

        val response = MissionGenerationWorkerController(executor).execute(jobId)

        assertEquals(204, response.statusCode.value())
    }
}
