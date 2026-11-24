package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionCandidateService(
    private val onboardingProfileRepository: OnboardingProfileRepository,
    private val alternativeGenerator: MissionAlternativeGenerationPort,
) {
    @Transactional(readOnly = true)
    fun candidates(
        guestUserId: Long,
        category: MissionCategory,
        item: MissionItem,
        baselineFrequency: Int,
        baselineAmountWon: Int,
    ): List<MissionCandidateSnapshot> {
        validateInput(category, item, baselineFrequency, baselineAmountWon)
        ensureOnboardingCompleted(guestUserId)

        val alternatives = alternativeGenerator.generate(
            MissionAlternativeGenerationRequest(
                item = item,
                knowledgeContexts = emptyList(),
                jobId = UUID.randomUUID(),
            ),
        ).alternatives
        check(alternatives.size == REQUIRED_CANDIDATE_COUNT) {
            "Exactly $REQUIRED_CANDIDATE_COUNT mission candidates are required"
        }

        val selectedAlternatives = alternatives.take(baselineFrequency)
        val allocations = MissionTargetAllocator.allocate(
            baselineFrequency,
            baselineAmountWon,
            selectedAlternatives.size,
        )

        return selectedAlternatives.zip(allocations).map { (alternative, allocation) ->
            MissionCandidateSnapshot(
                category = category,
                item = item,
                title = MissionTitleRenderer.render(alternative.titleTemplate, allocation.targetCount),
                description = alternative.description,
                actionCode = item.name,
                metricType = MissionMetricType.COUNT,
                targetCount = allocation.targetCount,
                targetUnit = TARGET_UNIT,
                estimatedSavingsWon = allocation.estimatedSavingsWon,
                savingsEstimateVersion = SAVINGS_ESTIMATE_VERSION,
            )
        }
    }

    private fun validateInput(
        category: MissionCategory,
        item: MissionItem,
        baselineFrequency: Int,
        baselineAmountWon: Int,
    ) {
        if (!category.active || !item.active || item.category != category ||
            baselineFrequency !in 1..10 || baselineAmountWon !in 1..2_000_000
        ) {
            throw BaseException(ErrorCode.MISSION_GENERATION_INPUT_INVALID)
        }
    }

    private fun ensureOnboardingCompleted(guestUserId: Long) {
        val profile = onboardingProfileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)
        if (profile.status != OnboardingStatus.COMPLETED || profile.birthDate == null || profile.address == null) {
            throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)
        }
    }

    companion object {
        private const val REQUIRED_CANDIDATE_COUNT = 3
        private const val TARGET_UNIT = "TIMES_PER_WEEK"
        private const val SAVINGS_ESTIMATE_VERSION = "V2_DETERMINISTIC"
    }
}

data class MissionCandidateSnapshot(
    val category: MissionCategory,
    val item: MissionItem,
    val title: String,
    val description: String,
    val actionCode: String,
    val metricType: MissionMetricType,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String,
)
