package backend.yapp.api.mission.generation.dto

import backend.yapp.core.mission.generation.service.MissionGenerationDispatchResult

data class MissionGenerationDispatchResponse(
    val published: Int,
    val publishFailed: Int,
    val requeued: Int,
    val terminalFailed: Int,
) {
    companion object {
        fun from(result: MissionGenerationDispatchResult): MissionGenerationDispatchResponse =
            MissionGenerationDispatchResponse(
                published = result.published,
                publishFailed = result.publishFailed,
                requeued = result.requeued,
                terminalFailed = result.terminalFailed,
            )
    }
}
