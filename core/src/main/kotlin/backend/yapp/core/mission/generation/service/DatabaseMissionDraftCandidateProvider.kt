package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftCandidateProvider
import backend.yapp.core.mission.generation.port.MissionExpenseEstimate

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
                val expenseEstimate = template.expenseEstimate(template.targetCount)
                MissionDraftCandidate(
                    templateId = template.id,
                    category = template.category,
                    templateTitle = template.title,
                    templateDescription = template.description,
                    actionCode = template.actionCode,
                    metricType = template.metricType,
                    targetCount = template.targetCount,
                    targetUnit = template.targetUnit,
                    estimatedSavingsWon = expenseEstimate?.estimatedSavingsWon ?: template.estimatedSavingsWon,
                    savingsEstimateVersion = template.savingsEstimateVersion,
                    expenseEstimate = expenseEstimate,
                )
            }
    }

    private fun backend.yapp.core.mission.generation.domain.MissionDraftTemplate.expenseEstimate(
        savingsUnits: Int,
    ): MissionExpenseEstimate? {
        val reference = referenceExpenseWon ?: return null
        val alternative = alternativeExpenseWon ?: return null
        val referenceLabel = referenceExpenseLabel ?: return null
        val alternativeLabel = alternativeExpenseLabel ?: return null
        val unit = expenseUnit ?: return null
        val basis = estimateBasis ?: return null
        if (targetFormula != backend.yapp.core.mission.generation.domain.MissionTargetFormula.REPLACE ||
            targetUnit != "TIMES_PER_WEEK" || reference <= alternative || savingsUnits <= 0
        ) return null
        val perUnit = reference - alternative
        return MissionExpenseEstimate(
            referenceLabel, alternativeLabel, reference, alternative, perUnit,
            Math.multiplyExact(savingsUnits, perUnit), unit, basis, savingsEstimateVersion,
        )
    }
}
