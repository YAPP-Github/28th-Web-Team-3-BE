package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import backend.yapp.core.mission.generation.service.MissionGenerationRequestedEvent
import backend.yapp.core.mission.generation.service.MissionGenerationWorkService
import java.util.UUID
import kotlin.test.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException

class MissionGenerationJobListenerTest {
    @Test
    fun `marks job failed when bounded executor rejects generation`() {
        val executor = mock(MissionGenerationExecutor::class.java)
        val workService = mock(MissionGenerationWorkService::class.java)
        val taskExecutor = TaskExecutor { throw TaskRejectedException("queue full") }
        val listener = MissionGenerationJobListener(executor, workService, taskExecutor)
        val jobId = UUID.randomUUID()

        listener.handle(MissionGenerationRequestedEvent(jobId))

        verify(workService).fail(jobId, "MISSION_GENERATION_QUEUE_FULL")
    }
}
