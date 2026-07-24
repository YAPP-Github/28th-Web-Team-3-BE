package backend.yapp.core.mission.generation.service

import backend.yapp.core.onboarding.domain.GoalPlan

data class MissionRecommendationSettings(
    val algorithmVersion: String = "rule-v1",
    val aggressivePlan: GoalPlan = GoalPlan.PLAN_1,
    val normalReduction: Int = 1,
    val aggressiveReduction: Int = 2,
    val normalReplacementCount: Int = 1,
    val aggressiveReplacementCount: Int = 2,
    val exactCooldownDays: Long = 56,
    val familyCooldownDays: Long = 28,
    val signalDecayDays: Long = 84,
    val categoryConcentrationPenalty: Double = 0.04,
    val archetypeConcentrationPenalty: Double = 0.08,
    val recentCategoryExposurePenalty: Double = 0.03,
    val explorationBonus: Double = 0.05,
    val explorationRate: Double = 0.20,
)
