package backend.yapp.api.mission.generation.controller

import backend.yapp.core.mission.generation.service.MissionGenerationDispatchResult
import backend.yapp.core.mission.generation.service.MissionGenerationDispatchService
import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import backend.yapp.core.mission.generation.service.MissionGenerationExecutionResult
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/mission-generation")
@ConditionalOnProperty(prefix = "app", name = ["role"], havingValue = "mission-worker")
class MissionGenerationWorkerController(
    private val executor: MissionGenerationExecutor,
) {
    @PostMapping("/jobs/{jobId}/execute")
    fun execute(@PathVariable jobId: UUID): ResponseEntity<Void> {
        return when (executor.execute(jobId)) {
            MissionGenerationExecutionResult.ACTIVE_LEASE -> ResponseEntity.status(503).build()
            MissionGenerationExecutionResult.COMPLETED,
            MissionGenerationExecutionResult.SKIPPED,
            -> ResponseEntity.noContent().build()
        }
    }
}

@RestController
@RequestMapping("/internal/mission-generation")
@ConditionalOnProperty(prefix = "app", name = ["role"], havingValue = "mission-dispatcher")
class MissionGenerationDispatcherController(
    private val service: MissionGenerationDispatchService,
) {
    @PostMapping("/dispatch")
    fun dispatch(): MissionGenerationDispatchResult = service.dispatch()
}
