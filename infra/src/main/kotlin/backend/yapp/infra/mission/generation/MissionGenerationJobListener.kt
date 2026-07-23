package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import backend.yapp.core.mission.generation.service.MissionGenerationRequestedEvent
import backend.yapp.core.mission.generation.service.MissionGenerationWorkService
import java.time.Clock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MissionGenerationJobListener(
    private val executor: MissionGenerationExecutor,
    private val workService: MissionGenerationWorkService,
    @Qualifier("missionGenerationTaskExecutor")
    private val taskExecutor: TaskExecutor,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: MissionGenerationRequestedEvent) {
        try {
            taskExecutor.execute { executor.execute(event.jobId) }
        } catch (ex: TaskRejectedException) {
            workService.fail(event.jobId, "MISSION_GENERATION_QUEUE_FULL")
        }
    }
}

@Component
class MissionGenerationRecovery(
    private val workService: MissionGenerationWorkService,
    private val properties: MissionGenerationProperties,
    private val clock: Clock,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun recoverStaleJobs() {
        recover()
    }

    @Scheduled(fixedDelayString = "\${mission.generation.recovery-interval-millis:60000}")
    fun recoverStaleJobsPeriodically() {
        recover()
    }

    private fun recover() {
        workService.failStaleActive(clock.instant().minus(properties.staleRunningTimeout))
    }
}
