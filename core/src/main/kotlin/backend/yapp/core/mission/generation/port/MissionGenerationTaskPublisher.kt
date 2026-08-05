package backend.yapp.core.mission.generation.port

import java.util.UUID

fun interface MissionGenerationTaskPublisher {
    fun publish(jobId: UUID, generation: Int): String
}
