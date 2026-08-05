package backend.yapp.core.auth.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.domain.GuestUserRepository
import backend.yapp.core.auth.domain.RefreshTokenRepository
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.goal.domain.MonthlySavingRepository
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTraceRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshotRepository
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuestWithdrawalService(
    private val guestUserRepository: GuestUserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val onboardingProfileRepository: OnboardingProfileRepository,
    private val onboardingGoalRepository: OnboardingGoalRepository,
    private val goalRepository: GoalRepository,
    private val monthlySavingRepository: MonthlySavingRepository,
    private val missionSurveyRepository: MissionSurveyRepository,
    private val missionGenerationJobRepository: MissionGenerationJobRepository,
    private val missionDraftRepository: MissionDraftRepository,
    private val missionRepository: MissionRepository,
    private val manualMissionRepository: ManualMissionRepository,
    private val missionOutcomeEventRepository: MissionOutcomeEventRepository,
    private val missionRecommendationSnapshotRepository: MissionRecommendationSnapshotRepository,
    private val missionRecommendationCandidateTraceRepository: MissionRecommendationCandidateTraceRepository,
) {
    @Transactional
    fun withdraw(guestUserId: Long) {
        if (!guestUserRepository.existsById(guestUserId)) throw BaseException(ErrorCode.UNAUTHORIZED)

        missionOutcomeEventRepository.deleteByGuestUserId(guestUserId)
        manualMissionRepository.deleteByGuestUserId(guestUserId)
        missionRepository.deleteByGuestUserId(guestUserId)
        missionRecommendationCandidateTraceRepository.deleteByGuestUserId(guestUserId)
        missionRecommendationSnapshotRepository.deleteByGuestUserId(guestUserId)
        missionDraftRepository.deleteByGuestUserId(guestUserId)
        missionGenerationJobRepository.deleteByGuestUserId(guestUserId)
        missionSurveyRepository.deleteByGuestUserId(guestUserId)
        monthlySavingRepository.deleteByGuestUserId(guestUserId)
        goalRepository.deleteByGuestUserId(guestUserId)
        onboardingGoalRepository.deleteByGuestUserId(guestUserId)
        onboardingProfileRepository.deleteByGuestUserId(guestUserId)
        refreshTokenRepository.deleteByGuestUserId(guestUserId)
        guestUserRepository.deleteById(guestUserId)
    }
}
