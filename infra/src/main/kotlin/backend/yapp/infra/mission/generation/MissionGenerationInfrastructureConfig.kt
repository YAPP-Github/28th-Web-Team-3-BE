package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftCandidateProvider
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import backend.yapp.core.mission.generation.service.DatabaseMissionDraftCandidateProvider
import backend.yapp.core.mission.generation.service.MissionRecommendationSettings
import backend.yapp.core.mission.generation.service.PersonalizedMissionDraftCandidateProvider
import backend.yapp.core.mission.generation.port.MissionRecommendationTracePort
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshotRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTraceRepository
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.service.DatabaseMissionRecommendationTrace
import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MissionGenerationProperties::class)
class MissionGenerationInfrastructureConfig {
    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation.recommendation",
        name = ["provider"],
        havingValue = "template",
    )
    fun databaseMissionDraftCandidateProvider(
        templateRepository: MissionDraftTemplateRepository,
    ): MissionDraftCandidateProvider = DatabaseMissionDraftCandidateProvider(templateRepository)

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation.recommendation",
        name = ["provider"],
        havingValue = "personalized",
        matchIfMissing = true,
    )
    fun personalizedMissionDraftCandidateProvider(
        templateRepository: MissionDraftTemplateRepository,
        surveyRepository: MissionSurveyRepository,
        goalRepository: OnboardingGoalRepository,
        properties: MissionGenerationProperties,
        missionSemanticRetriever: MissionSemanticRetriever,
        missionRecommendationTracePort: MissionRecommendationTracePort,
        missionRepository: MissionRepository,
        manualMissionRepository: ManualMissionRepository,
        missionOutcomeEventRepository: MissionOutcomeEventRepository,
        clock: Clock,
    ): MissionDraftCandidateProvider {
        val recommendation = properties.recommendation
        return PersonalizedMissionDraftCandidateProvider(
            templateRepository,
            surveyRepository,
            goalRepository,
            MissionRecommendationSettings(
                algorithmVersion = recommendation.algorithmVersion,
                normalReduction = recommendation.normalReduction,
                aggressiveReduction = recommendation.aggressiveReduction,
                normalReplacementCount = recommendation.normalReplacementCount,
                aggressiveReplacementCount = recommendation.aggressiveReplacementCount,
                exactCooldownDays = recommendation.exactCooldownDays,
                familyCooldownDays = recommendation.familyCooldownDays,
                signalDecayDays = recommendation.signalDecayDays,
                categoryConcentrationPenalty = recommendation.categoryConcentrationPenalty,
                archetypeConcentrationPenalty = recommendation.archetypeConcentrationPenalty,
                recentCategoryExposurePenalty = recommendation.recentCategoryExposurePenalty,
                explorationBonus = recommendation.explorationBonus,
                explorationRate = recommendation.explorationRate,
            ),
            missionSemanticRetriever,
            missionRecommendationTracePort,
            missionRepository,
            manualMissionRepository,
            missionOutcomeEventRepository,
            clock,
        )
    }

    @Bean
    fun missionRecommendationTracePort(
        snapshotRepository: MissionRecommendationSnapshotRepository,
        candidateRepository: MissionRecommendationCandidateTraceRepository,
        clock: Clock,
    ): MissionRecommendationTracePort =
        DatabaseMissionRecommendationTrace(snapshotRepository, candidateRepository, clock)

}
