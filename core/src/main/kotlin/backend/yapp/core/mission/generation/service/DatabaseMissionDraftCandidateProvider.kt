package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftCandidateProvider
import org.springframework.stereotype.Component

@Component
class DatabaseMissionDraftCandidateProvider(
    private val templateRepository: MissionDraftTemplateRepository,
) : MissionDraftCandidateProvider {
    override fun candidates(
        guestUserId: Long,
        categories: Set<MissionCategory>,
    ): List<MissionDraftCandidate> {
        if (categories.isEmpty()) return emptyList()
        return templateRepository.findByCategoryInAndActiveTrueOrderByCategoryAscSortOrderAsc(categories)
            .map { template ->
                MissionDraftCandidate(
                    templateId = template.id,
                    category = template.category,
                    templateTitle = template.title,
                    templateDescription = template.description,
                    actionCode = template.actionCode,
                    metricType = template.metricType,
                    targetCount = template.targetCount,
                    targetUnit = template.targetUnit,
                    estimatedSavingsWon = template.estimatedSavingsWon,
                )
            }
    }
}
