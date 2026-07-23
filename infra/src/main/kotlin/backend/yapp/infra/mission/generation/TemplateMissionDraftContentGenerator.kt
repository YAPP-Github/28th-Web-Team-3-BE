package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionDraftContentResult
import backend.yapp.core.mission.generation.port.MissionDraftCopy
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource

class TemplateMissionDraftContentGenerator : MissionDraftContentGenerator {
    override fun generate(request: MissionDraftContentRequest): MissionDraftContentResult =
        MissionDraftContentResult(
            copies = request.candidates.map { candidate ->
                MissionDraftCopy(
                    templateId = candidate.templateId,
                    title = candidate.templateTitle,
                    description = candidate.templateDescription,
                )
            },
            source = MissionDraftGenerationSource.MOCK,
        )
}
