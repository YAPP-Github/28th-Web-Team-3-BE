package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.time.Duration
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

enum class MissionGenerationLatencyStage {
    DISPATCH,
    QUEUE,
    RETRIEVAL,
    VERIFICATION,
    AI_GENERATION,
    PERSISTENCE,
    WORKER_TOTAL,
    END_TO_END,
}

enum class MissionGenerationLatencyOutcome {
    SUCCEEDED,
    FAILED,
    RETRY,
    DUPLICATE,
    SKIPPED,
    UNPAIRED,
}

fun interface MissionGenerationLatencyRecorder {
    fun record(
        stage: MissionGenerationLatencyStage,
        outcome: MissionGenerationLatencyOutcome,
        generationSource: MissionDraftGenerationSource?,
        duration: Duration,
        jobId: UUID,
    )
}

object NoopMissionGenerationLatencyRecorder : MissionGenerationLatencyRecorder {
    override fun record(
        stage: MissionGenerationLatencyStage,
        outcome: MissionGenerationLatencyOutcome,
        generationSource: MissionDraftGenerationSource?,
        duration: Duration,
        jobId: UUID,
    ) = Unit
}

@Service
class SafeMissionGenerationLatencyRecorder : MissionGenerationLatencyRecorder {
    override fun record(
        stage: MissionGenerationLatencyStage,
        outcome: MissionGenerationLatencyOutcome,
        generationSource: MissionDraftGenerationSource?,
        duration: Duration,
        jobId: UUID,
    ) {
        log.info(
            "mission_generation_latency stage={} outcome={} generation_source={} duration_ms={} job_id={}",
            stage.name.lowercase(),
            outcome.name.lowercase(),
            generationSource?.name?.lowercase() ?: NONE,
            duration.toMillis().coerceAtLeast(0),
            jobId,
        )
    }

    companion object {
        private const val NONE = "none"
        private val log = LoggerFactory.getLogger(SafeMissionGenerationLatencyRecorder::class.java)
    }
}
