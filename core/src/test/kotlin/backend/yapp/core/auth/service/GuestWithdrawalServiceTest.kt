package backend.yapp.core.auth.service

import backend.yapp.core.auth.domain.GuestUserRepository
import backend.yapp.core.auth.domain.RefreshTokenRepository
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.goal.domain.MonthlySavingRepository
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionBlogTipRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationCandidateTraceRepository
import backend.yapp.core.mission.generation.domain.MissionRecommendationSnapshotRepository
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionWeeklyCompletionRepository
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class GuestWithdrawalServiceTest {
    @Test
    fun `withdraw deletes every user-owned aggregate before account`() {
        val users = mock(GuestUserRepository::class.java)
        `when`(users.existsById(7)).thenReturn(true)
        val tokens = mock(RefreshTokenRepository::class.java)
        val profiles = mock(OnboardingProfileRepository::class.java)
        val onboardingGoals = mock(OnboardingGoalRepository::class.java)
        val goals = mock(GoalRepository::class.java)
        val savings = mock(MonthlySavingRepository::class.java)
        val surveys = mock(MissionSurveyRepository::class.java)
        val jobs = mock(MissionGenerationJobRepository::class.java)
        val drafts = mock(MissionDraftRepository::class.java)
        val missions = mock(MissionRepository::class.java)
        val manuals = mock(ManualMissionRepository::class.java)
        val outcomes = mock(MissionOutcomeEventRepository::class.java)
        val weeklyCompletions = mock(MissionWeeklyCompletionRepository::class.java)
        val blogTips = mock(MissionBlogTipRepository::class.java)
        val snapshots = mock(MissionRecommendationSnapshotRepository::class.java)
        val candidates = mock(MissionRecommendationCandidateTraceRepository::class.java)
        GuestWithdrawalService(
            users, tokens, profiles, onboardingGoals, goals, savings, surveys, jobs, drafts,
            missions, manuals, weeklyCompletions, blogTips, outcomes, snapshots, candidates,
        ).withdraw(7)
        val order = inOrder(
            outcomes, weeklyCompletions, blogTips, manuals, missions, candidates, snapshots, drafts, jobs, surveys, savings,
            goals, onboardingGoals, profiles, tokens, users,
        )
        order.verify(outcomes).deleteByGuestUserId(7)
        order.verify(weeklyCompletions).deleteByGuestUserId(7)
        order.verify(blogTips).deleteByGuestUserId(7)
        order.verify(manuals).deleteByGuestUserId(7)
        order.verify(missions).deleteByGuestUserId(7)
        order.verify(candidates).deleteByGuestUserId(7)
        order.verify(snapshots).deleteByGuestUserId(7)
        order.verify(drafts).deleteByGuestUserId(7)
        order.verify(jobs).deleteByGuestUserId(7)
        order.verify(surveys).deleteByGuestUserId(7)
        order.verify(savings).deleteByGuestUserId(7)
        order.verify(goals).deleteByGuestUserId(7)
        order.verify(onboardingGoals).deleteByGuestUserId(7)
        order.verify(profiles).deleteByGuestUserId(7)
        order.verify(tokens).deleteByGuestUserId(7)
        order.verify(users).deleteById(7)
    }
}
