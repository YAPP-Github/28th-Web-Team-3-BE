package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraftTemplate
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionTargetFormula
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftCandidateProvider
import backend.yapp.core.mission.generation.port.MissionExpenseEstimate
import backend.yapp.core.mission.generation.port.MissionSemanticDocument
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.core.mission.generation.port.MissionRecommendationTracePort
import backend.yapp.core.mission.generation.port.MissionRecommendationTraceCandidate
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import kotlin.math.max
import kotlin.math.min
import java.time.Clock
import java.time.Duration
import java.time.Instant

class PersonalizedMissionDraftCandidateProvider(
    private val templateRepository: MissionDraftTemplateRepository,
    private val surveyRepository: MissionSurveyRepository,
    private val goalRepository: OnboardingGoalRepository,
    private val settings: MissionRecommendationSettings,
    private val semanticRetriever: MissionSemanticRetriever,
    private val tracePort: MissionRecommendationTracePort,
    private val missionRepository: MissionRepository,
    private val manualMissionRepository: ManualMissionRepository,
    private val outcomeEventRepository: MissionOutcomeEventRepository,
    private val clock: Clock,
) : MissionDraftCandidateProvider {
    override fun candidates(
        guestUserId: Long,
        categories: Set<MissionCategory>,
    ): List<MissionDraftCandidate> {
        if (categories.isEmpty()) return emptyList()
        val survey = surveyRepository.findByGuestUserId(guestUserId) ?: return emptyList()
        val rows = survey.answerRows()
        val answerCodes = rows.map { it.answerCode }.toSet()
        val baselines = rows.mapNotNull { row ->
            row.numericValue?.let { row.answerCode to it }
        }.toMap()
        val plan = goalRepository.findByGuestUserId(guestUserId)?.plan
        val aggressive = plan == settings.aggressivePlan
        val now = clock.instant()
        val cooldownCutoff = now.minus(Duration.ofDays(settings.exactCooldownDays))
        val recentMissions = missionRepository.findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId)
        val recentActionCodes = recentMissions
            .filter { it.createdAt >= cooldownCutoff }
            .map { it.actionCode }
            .toSet()
        val recentCategoryCounts = recentMissions
            .filter { it.createdAt >= now.minus(Duration.ofDays(settings.familyCooldownDays)) }
            .groupingBy { it.category }
            .eachCount()
        val manualMissions = manualMissionRepository.findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId)
            .filter { it.createdAt >= now.minus(Duration.ofDays(settings.signalDecayDays)) }
        val manualTags = manualMissions
            .flatMap { it.structuredTags.split(',') }
            .filter(String::isNotBlank)
            .toSet()
        val outcomeTimes = outcomeEventRepository.findAllByGuestUserIdOrderByOccurredAtDesc(guestUserId)
            .associate { it.missionId to it.occurredAt }
        val catalog = templateRepository.findByCategoryInAndActiveTrueOrderByCategoryAscSortOrderAsc(categories)
        val familyByAction = catalog.associate { it.actionCode to it.cooldownFamily }
        val recentFamilies = recentMissions
            .filter { it.createdAt >= now.minus(Duration.ofDays(settings.familyCooldownDays)) }
            .mapNotNull { familyByAction[it.actionCode] }
            .toSet()
        val eligibleTemplates = catalog
            .filter { it.isEligible(answerCodes) }
            .filterNot { it.targetFormula in baselineRequiredFormulas && (baselines[it.targetCode] ?: 0) == 0 }
            .filterNot { it.actionCode in recentActionCodes }
            .filterNot { it.cooldownFamily in recentFamilies }
        val semanticResult = runCatching {
            semanticRetriever.retrieve(
                MissionSemanticRetrievalRequest(
                    query = (answerCodes + manualTags).sorted().joinToString(" "),
                    candidates = eligibleTemplates.map { MissionSemanticDocument(it.id, it.embeddingText) },
                ),
            )
        }.getOrNull()
        val semanticScores = semanticResult?.scores.orEmpty()
        val scored = eligibleTemplates
            .asSequence()
            .mapNotNull { template ->
                template.toScoredCandidate(
                    baseline = baselines[template.targetCode],
                    answerCodes = answerCodes,
                    aggressive = aggressive,
                    semanticScore = semanticScores[template.id] ?: 0.0,
                    recentCategoryCount = recentCategoryCounts[template.category] ?: 0,
                    retrieved = template.id in semanticScores,
                    preferenceSignal = template.preferenceSignal(
                        recentMissions,
                        manualMissions,
                        outcomeTimes,
                        familyByAction,
                        now,
                    ),
                )
            }
            .toList()
        val result = rerank(scored, applyCategoryConcentration = categories.size > 1)
        tracePort.recordRun(
            guestUserId,
            settings.algorithmVersion,
            semanticResult?.provider ?: "rules-only",
            semanticResult?.modelVersion ?: "none",
            eligibleTemplates.map { it.id },
            semanticScores.keys,
            weeklyContextSnapshot(
                plan = plan?.name ?: "UNKNOWN",
                answerCodes = answerCodes,
                baselines = baselines,
                recentMissions = recentMissions,
                manualMissions = manualMissions,
                outcomeTimes = outcomeTimes,
            ),
            result.map {
                MissionRecommendationTraceCandidate(
                    candidate = it.candidate,
                    rawScore = it.rawScore,
                    adjustedScore = it.adjustedScore,
                    retrieved = it.retrieved,
                    explorationApplied = it.explorationApplied,
                    appliedPenalties = listOf(
                        "recentCategory=${it.recentCategoryPenalty}",
                        "categoryConcentration=${it.categoryConcentrationPenalty}",
                        "archetypeConcentration=${it.archetypeConcentrationPenalty}",
                        "exploration=${if (it.explorationApplied) settings.explorationBonus else 0.0}",
                    ).joinToString(";"),
                )
            },
        )
        return result.map { it.candidate }
    }

    private fun MissionDraftTemplate.isEligible(answerCodes: Set<String>): Boolean {
        val excluded = excludedCodes.codes()
        if (excluded.any(answerCodes::contains)) return false
        val eligible = eligibleCodes.codes()
        if (targetCode != "GENERAL" && targetCode !in answerCodes) return false
        val requiredAlternative = eligible - targetCode
        return requiredAlternative.isEmpty() || requiredAlternative.any(answerCodes::contains)
    }

    private fun MissionDraftTemplate.toScoredCandidate(
        baseline: Int?,
        answerCodes: Set<String>,
        aggressive: Boolean,
        semanticScore: Double,
        recentCategoryCount: Int,
        retrieved: Boolean,
        preferenceSignal: Double,
    ): ScoredCandidate? {
        if (targetFormula in baselineRequiredFormulas && (baseline == null || baseline == 0)) return null
        val reduction = if (aggressive) settings.aggressiveReduction else settings.normalReduction
        val replacement = if (aggressive) {
            settings.aggressiveReplacementCount
        } else {
            settings.normalReplacementCount
        }
        val target = when (targetFormula) {
            MissionTargetFormula.REDUCE_MAX -> max(0, checkNotNull(baseline) - reduction)
            MissionTargetFormula.REPLACE -> min(checkNotNull(baseline), replacement)
            MissionTargetFormula.FIXED,
            MissionTargetFormula.CHECK,
            MissionTargetFormula.RECORD,
            -> targetCount
        }
        val savingsUnits = when (targetFormula) {
            MissionTargetFormula.REDUCE_MAX -> checkNotNull(baseline) - target
            MissionTargetFormula.REPLACE -> target
            else -> target
        }
        val eligible = eligibleCodes.codes()
        val onboardingFit = if (targetCode in answerCodes) 1.0 else if (targetCode == "GENERAL") 0.6 else 0.2
        val explicitPreference = if (eligible.isEmpty()) 0.5 else {
            eligible.count(answerCodes::contains).toDouble() / eligible.size
        }
        val feasibility = min((baseline ?: 1).toDouble() / 7.0, 1.0)
        val goalContribution = min(averageSavingsPerUnit.toDouble() / 20_000.0, 1.0)
        val novelty = 1.0
        val missionQuality = 1.0
        val rawScore =
            0.30 * onboardingFit +
                0.20 * explicitPreference +
                0.15 * feasibility +
                0.15 * goalContribution +
                0.10 * novelty +
                0.10 * missionQuality +
                0.10 * semanticScore.coerceIn(0.0, 1.0) +
                preferenceSignal.coerceIn(-0.20, 0.30)
        val explorationApplied = (id % 100L).toDouble() / 100.0 < settings.explorationRate
        val adjustedScore = rawScore - (recentCategoryCount * settings.recentCategoryExposurePenalty) +
            if (explorationApplied) settings.explorationBonus else 0.0
        val expenseEstimate = expenseEstimate(savingsUnits)
        return ScoredCandidate(
            candidate = MissionDraftCandidate(
                templateId = id,
                category = category,
                templateTitle = title,
                templateDescription = description,
                actionCode = actionCode,
                metricType = metricType,
                targetCount = target,
                targetUnit = targetUnit,
                estimatedSavingsWon = expenseEstimate?.estimatedSavingsWon ?: Math.multiplyExact(savingsUnits, averageSavingsPerUnit),
                savingsEstimateVersion = savingsEstimateVersion,
                expenseEstimate = expenseEstimate,
            ),
            rawScore = rawScore,
            adjustedScore = adjustedScore,
            sortOrder = sortOrder,
            retrieved = retrieved,
            explorationApplied = explorationApplied,
            cooldownFamily = cooldownFamily,
            recentCategoryPenalty = recentCategoryCount * settings.recentCategoryExposurePenalty,
        )
    }

    private fun MissionDraftTemplate.expenseEstimate(savingsUnits: Int): MissionExpenseEstimate? {
        if (targetFormula != MissionTargetFormula.REPLACE || targetUnit != "TIMES_PER_WEEK") return null
        val reference = referenceExpenseWon ?: return null
        val alternative = alternativeExpenseWon ?: return null
        val referenceLabel = referenceExpenseLabel ?: return null
        val alternativeLabel = alternativeExpenseLabel ?: return null
        val unit = expenseUnit ?: return null
        val basis = estimateBasis ?: return null
        val perUnit = reference - alternative
        if (reference <= alternative || savingsUnits <= 0) return null
        return MissionExpenseEstimate(
            referenceExpenseLabel = referenceLabel,
            alternativeExpenseLabel = alternativeLabel,
            referenceExpenseWon = reference,
            alternativeExpenseWon = alternative,
            estimatedSavingsPerUnitWon = perUnit,
            estimatedSavingsWon = Math.multiplyExact(savingsUnits, perUnit),
            unit = unit,
            estimateBasis = basis,
            savingsEstimateVersion = savingsEstimateVersion,
        )
    }

    private fun rerank(
        candidates: List<ScoredCandidate>,
        applyCategoryConcentration: Boolean,
    ): List<ScoredCandidate> {
        val remaining = candidates.toMutableList()
        val selected = mutableListOf<ScoredCandidate>()
        val categoryCounts = mutableMapOf<MissionCategory, Int>()
        val familyCounts = mutableMapOf<String, Int>()
        while (remaining.isNotEmpty()) {
            val next = remaining.maxWithOrNull(
                compareBy<ScoredCandidate> {
                    dynamicAdjusted(it, categoryCounts, familyCounts, applyCategoryConcentration)
                }.thenBy { it.rawScore }
                    .thenByDescending { -it.sortOrder }
                    .thenByDescending { -it.candidate.templateId },
            ) ?: break
            remaining.remove(next)
            val adjusted = dynamicAdjusted(next, categoryCounts, familyCounts, applyCategoryConcentration)
            selected += next.copy(
                adjustedScore = adjusted,
                categoryConcentrationPenalty = if (applyCategoryConcentration) {
                    categoryCounts.getOrDefault(next.candidate.category, 0) *
                        settings.categoryConcentrationPenalty
                } else {
                    0.0
                },
                archetypeConcentrationPenalty = familyCounts.getOrDefault(next.cooldownFamily, 0) *
                    settings.archetypeConcentrationPenalty,
            )
            categoryCounts[next.candidate.category] = categoryCounts.getOrDefault(next.candidate.category, 0) + 1
            familyCounts[next.cooldownFamily] = familyCounts.getOrDefault(next.cooldownFamily, 0) + 1
        }
        return selected
    }

    private fun dynamicAdjusted(
        candidate: ScoredCandidate,
        categoryCounts: Map<MissionCategory, Int>,
        familyCounts: Map<String, Int>,
        applyCategoryConcentration: Boolean,
    ): Double {
        val categoryPenalty = if (applyCategoryConcentration) {
            categoryCounts.getOrDefault(candidate.candidate.category, 0) * settings.categoryConcentrationPenalty
        } else {
            0.0
        }
        val familyPenalty = familyCounts.getOrDefault(candidate.cooldownFamily, 0) *
            settings.archetypeConcentrationPenalty
        return candidate.adjustedScore - categoryPenalty - familyPenalty
    }

    private fun decay(createdAt: Instant, now: Instant): Double {
        val ageDays = Duration.between(createdAt, now).toDays().coerceAtLeast(0)
        return (1.0 - ageDays.toDouble() / settings.signalDecayDays).coerceIn(0.0, 1.0)
    }

    private fun MissionDraftTemplate.preferenceSignal(
        recentMissions: List<backend.yapp.core.mission.generation.domain.Mission>,
        manualMissions: List<backend.yapp.core.mission.generation.domain.ManualMission>,
        outcomeTimes: Map<java.util.UUID, Instant>,
        familyByAction: Map<String, String>,
        now: Instant,
    ): Double {
        val recommendedSignal = recentMissions.sumOf { mission ->
            val sameArchetype = mission.actionCode == actionCode ||
                familyByAction[mission.actionCode] == cooldownFamily
            if (!sameArchetype) {
                0.0
            } else {
                val value = when (mission.status) {
                    MissionStatus.COMPLETED -> 0.12
                    MissionStatus.INCOMPLETE -> -0.04
                    MissionStatus.ACTIVE -> 0.0
                }
                value * decay(outcomeTimes[mission.id] ?: mission.completedAt ?: mission.createdAt, now)
            }
        }
        val templateSignals = eligibleCodes.codes() + targetCode
        val manualSignal = manualMissions.sumOf { mission ->
            val tags = mission.structuredTags.split(',').toSet()
            if ((tags intersect templateSignals).isEmpty()) {
                0.0
            } else {
                val value = when (mission.status) {
                    MissionStatus.COMPLETED -> 0.12
                    MissionStatus.INCOMPLETE -> -0.02
                    MissionStatus.ACTIVE -> 0.04
                }
                value * decay(outcomeTimes[mission.id] ?: mission.completedAt ?: mission.createdAt, now)
            }
        }
        return (recommendedSignal + manualSignal).coerceIn(-0.20, 0.30)
    }

    private fun weeklyContextSnapshot(
        plan: String,
        answerCodes: Set<String>,
        baselines: Map<String, Int>,
        recentMissions: List<backend.yapp.core.mission.generation.domain.Mission>,
        manualMissions: List<backend.yapp.core.mission.generation.domain.ManualMission>,
        outcomeTimes: Map<java.util.UUID, Instant>,
    ): String = listOf(
        "plan=$plan",
        "answers=${answerCodes.sorted().joinToString(",")}",
        "baselines=${baselines.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }}",
        "recommended=${recentMissions.joinToString(",") {
            "${it.actionCode}:${it.status}:${outcomeTimes[it.id] ?: it.completedAt ?: it.createdAt}"
        }}",
        "manual=${manualMissions.joinToString(",") {
            "${it.structuredTags}:${it.status}:${outcomeTimes[it.id] ?: it.completedAt ?: it.createdAt}"
        }}",
    ).joinToString("|").take(4000)

    private fun String.codes(): Set<String> =
        split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private data class ScoredCandidate(
        val candidate: MissionDraftCandidate,
        val rawScore: Double,
        val adjustedScore: Double,
        val sortOrder: Int,
        val retrieved: Boolean,
        val explorationApplied: Boolean,
        val cooldownFamily: String,
        val recentCategoryPenalty: Double,
        val categoryConcentrationPenalty: Double = 0.0,
        val archetypeConcentrationPenalty: Double = 0.0,
    )

    companion object {
        private val baselineRequiredFormulas =
            setOf(MissionTargetFormula.REDUCE_MAX, MissionTargetFormula.REPLACE)
    }
}
