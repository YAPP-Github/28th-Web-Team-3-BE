package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.ManualMission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraftTemplate
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionOutcomeEvent
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import backend.yapp.core.mission.generation.domain.MissionTargetFormula
import backend.yapp.core.mission.generation.port.MissionRecommendationTracePort
import backend.yapp.core.mission.generation.port.MissionSemanticRetrievalResult
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.core.mission.survey.domain.MissionSurvey
import backend.yapp.core.mission.survey.domain.MissionSurveyAnswerValue
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.onboarding.domain.GoalPlan
import backend.yapp.core.onboarding.domain.OnboardingGoal
import backend.yapp.core.onboarding.domain.OnboardingGoalRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PersonalizedMissionDraftCandidateProviderTest {
    private val now = Instant.parse("2026-07-23T00:00:00Z")

    @Test
    fun `zero baseline excludes reduction mission and keeps record fallback`() {
        val result = provider(GoalPlan.PLAN_1, baseline = 0).candidates(1, setOf(MissionCategory.MEAL))

        assertEquals(listOf("RECORD"), result.map { it.actionCode })
    }

    @Test
    fun `plan changes target only and aggressive target never exceeds baseline`() {
        val aggressive = provider(GoalPlan.PLAN_1, baseline = 3).candidates(1, setOf(MissionCategory.MEAL))
        val normal = provider(GoalPlan.PLAN_2, baseline = 3).candidates(1, setOf(MissionCategory.MEAL))

        assertEquals(normal.map { it.actionCode }, aggressive.map { it.actionCode })
        assertEquals(1, aggressive.first { it.actionCode == "REDUCE" }.targetCount)
        assertEquals(2, normal.first { it.actionCode == "REDUCE" }.targetCount)
        assertTrue(aggressive.all { it.targetCount <= 3 })
    }

    @Test
    fun `safety exclusion is a hard filter before semantic retrieval`() {
        val templates = listOf(
            template(
                1,
                "REDUCE",
                MissionTargetFormula.REDUCE_MAX,
                "DELIVERY",
                excludedCodes = "HEALTH_OR_DIET",
            ),
            template(2, "RECORD", MissionTargetFormula.RECORD, "GENERAL"),
        )
        val result = provider(
            GoalPlan.PLAN_1,
            baseline = 3,
            extraAnswerCodes = listOf("HEALTH_OR_DIET"),
            templates = templates,
        ).candidates(1, setOf(MissionCategory.MEAL))

        assertEquals(listOf("RECORD"), result.map { it.actionCode })
    }

    @Test
    fun `replacement requires an explicitly selected alternative`() {
        val replacement = template(
            1,
            "PICKUP",
            MissionTargetFormula.REPLACE,
            "DELIVERY",
            eligibleCodes = "DELIVERY,PICKUP",
        )

        val withoutAlternative = provider(
            GoalPlan.PLAN_1,
            3,
            templates = listOf(replacement),
        ).candidates(1, setOf(MissionCategory.MEAL))
        val withAlternative = provider(
            GoalPlan.PLAN_1,
            3,
            extraAnswerCodes = listOf("PICKUP"),
            templates = listOf(replacement),
        ).candidates(1, setOf(MissionCategory.MEAL))

        assertTrue(withoutAlternative.isEmpty())
        assertEquals("PICKUP", withAlternative.single().actionCode)
    }

    @Test
    fun `semantic failure falls back to rule candidates`() {
        val provider = provider(
            GoalPlan.PLAN_1,
            3,
            semanticRetriever = MissionSemanticRetriever { error("embedding unavailable") },
        )

        val result = provider.candidates(1, setOf(MissionCategory.MEAL))

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `recent completion boosts the matching archetype using completion time decay`() {
        val preferred = template(31, "PREFERRED", MissionTargetFormula.FIXED, "GENERAL")
        val other = template(32, "OTHER", MissionTargetFormula.FIXED, "GENERAL")
        val completed = Mission(
            id = java.util.UUID.randomUUID(),
            jobId = java.util.UUID.randomUUID(),
            draftId = java.util.UUID.randomUUID(),
            guestUserId = 1,
            category = MissionCategory.MEAL,
            title = "완료",
            description = "완료",
            actionCode = "PREFERRED",
            metricType = MissionMetricType.COUNT,
            targetCount = 1,
            targetUnit = "TIMES_PER_WEEK",
            estimatedSavingsWon = 1000,
            status = MissionStatus.COMPLETED,
            weekEndsAt = now.plusSeconds(3600),
            completedAt = now.minusSeconds(86400),
            createdAt = now.minusSeconds(70L * 86400),
        )

        val result = provider(
            GoalPlan.PLAN_1,
            baseline = 3,
            templates = listOf(other, preferred),
            recentMissions = listOf(completed),
        ).candidates(1, setOf(MissionCategory.MEAL))

        assertEquals("PREFERRED", result.first().actionCode)
    }

    @Test
    fun `recent incomplete outcome uses event time for matching archetype decay`() {
        val preferred = template(31, "PREFERRED", MissionTargetFormula.FIXED, "GENERAL")
        val other = template(32, "OTHER", MissionTargetFormula.FIXED, "GENERAL")
        val incomplete = Mission(
            id = UUID.randomUUID(),
            jobId = UUID.randomUUID(),
            draftId = UUID.randomUUID(),
            guestUserId = 1,
            category = MissionCategory.MEAL,
            title = "미완료",
            description = "미완료",
            actionCode = "PREFERRED",
            metricType = MissionMetricType.COUNT,
            targetCount = 1,
            targetUnit = "TIMES_PER_WEEK",
            estimatedSavingsWon = 1000,
            status = MissionStatus.INCOMPLETE,
            weekEndsAt = now.minusSeconds(3600),
            createdAt = now.minusSeconds(70L * 86400),
        )
        val outcome = MissionOutcomeEvent(
            id = UUID.randomUUID(),
            guestUserId = 1,
            missionSource = "RECOMMENDED",
            missionId = incomplete.id,
            finalStatus = MissionStatus.INCOMPLETE,
            occurredAt = now.minusSeconds(86400),
        )

        val result = provider(
            GoalPlan.PLAN_1,
            baseline = 3,
            templates = listOf(preferred, other),
            recentMissions = listOf(incomplete),
            outcomeEvents = listOf(outcome),
        ).candidates(1, setOf(MissionCategory.MEAL))

        assertEquals("OTHER", result.first().actionCode)
    }

    @Test
    fun `weekly context stores structured manual tags without raw manual text`() {
        val trace = RecordingTracePort()
        val rawText = "개인 메모 원문은 추천 추적에 남기지 않는다"
        val manualMission = ManualMission(
            id = UUID.randomUUID(),
            guestUserId = 1,
            category = MissionCategory.MEAL,
            missionText = rawText,
            structuredTags = "DELIVERY",
            targetCount = 1,
            targetUnit = "TIMES_PER_WEEK",
            weekEndsAt = now.plusSeconds(86400),
            createdAt = now.minusSeconds(3600),
        )

        provider(
            GoalPlan.PLAN_1,
            baseline = 3,
            manualMissions = listOf(manualMission),
            tracePort = trace,
        ).candidates(1, setOf(MissionCategory.MEAL))

        assertFalse(rawText in trace.weeklyContextSnapshot)
        assertTrue("manual=DELIVERY:ACTIVE:" in trace.weeklyContextSnapshot)
    }

    private fun provider(
        plan: GoalPlan,
        baseline: Int,
        extraAnswerCodes: List<String> = emptyList(),
        templates: List<MissionDraftTemplate> = listOf(
            template(1, "REDUCE", MissionTargetFormula.REDUCE_MAX, "DELIVERY"),
            template(2, "RECORD", MissionTargetFormula.RECORD, "GENERAL"),
        ),
        semanticRetriever: MissionSemanticRetriever = MissionSemanticRetriever {
            MissionSemanticRetrievalResult(emptyMap(), "test", "test")
        },
        recentMissions: List<Mission> = emptyList(),
        manualMissions: List<ManualMission> = emptyList(),
        outcomeEvents: List<MissionOutcomeEvent> = emptyList(),
        tracePort: MissionRecommendationTracePort = mock(MissionRecommendationTracePort::class.java),
    ): PersonalizedMissionDraftCandidateProvider {
        val templateRepository = mock(MissionDraftTemplateRepository::class.java)
        val surveyRepository = mock(MissionSurveyRepository::class.java)
        val goalRepository = mock(OnboardingGoalRepository::class.java)
        val missionRepository = mock(MissionRepository::class.java)
        val manualRepository = mock(ManualMissionRepository::class.java)
        val outcomeRepository = mock(MissionOutcomeEventRepository::class.java)
        `when`(templateRepository.findByCategoryInAndActiveTrueOrderByCategoryAscSortOrderAsc(setOf(MissionCategory.MEAL)))
            .thenReturn(templates)
        `when`(surveyRepository.findByGuestUserId(1)).thenReturn(survey(baseline, extraAnswerCodes))
        `when`(goalRepository.findByGuestUserId(1)).thenReturn(
            OnboardingGoal(1, plan, 12, 100, 100, 1200, "v1"),
        )
        `when`(missionRepository.findAllByGuestUserIdOrderByCreatedAtDesc(1)).thenReturn(recentMissions)
        `when`(manualRepository.findAllByGuestUserIdOrderByCreatedAtDesc(1)).thenReturn(manualMissions)
        `when`(outcomeRepository.findAllByGuestUserIdOrderByOccurredAtDesc(1)).thenReturn(outcomeEvents)
        return PersonalizedMissionDraftCandidateProvider(
            templateRepository,
            surveyRepository,
            goalRepository,
            MissionRecommendationSettings(),
            semanticRetriever,
            tracePort,
            missionRepository,
            manualRepository,
            outcomeRepository,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    private fun survey(baseline: Int, extraAnswerCodes: List<String>): MissionSurvey =
        MissionSurvey(guestUserId = 1).also {
            it.addAnswers(
                listOf(
                    MissionSurveyAnswerValue("MEAL", "MEAL_TARGET", "OPTION", "DELIVERY"),
                    MissionSurveyAnswerValue(
                        "MEAL",
                        "MEAL_FREQUENCY",
                        "NUMBER",
                        "DELIVERY",
                        baseline,
                        "TIMES_PER_WEEK",
                    ),
                ) + extraAnswerCodes.map { code ->
                    MissionSurveyAnswerValue("MEAL", "EXTRA", "OPTION", code)
                },
                now,
            )
        }

    private fun template(
        id: Long,
        action: String,
        formula: MissionTargetFormula,
        target: String,
        eligibleCodes: String = if (target == "GENERAL") "" else target,
        excludedCodes: String = "",
    ) = MissionDraftTemplate(
        category = MissionCategory.MEAL,
        title = action,
        description = action,
        actionCode = action,
        metricType = MissionMetricType.COUNT,
        targetCount = 1,
        targetUnit = "TIMES_PER_WEEK",
        estimatedSavingsWon = 1000,
        targetCode = target,
        eligibleCodes = eligibleCodes,
        excludedCodes = excludedCodes,
        targetFormula = formula,
        averageSavingsPerUnit = 1000,
        sortOrder = id.toInt(),
        id = id,
    )

    private class RecordingTracePort : MissionRecommendationTracePort {
        var weeklyContextSnapshot: String = ""

        override fun recordRun(
            guestUserId: Long,
            algorithmVersion: String,
            semanticProvider: String,
            semanticModelVersion: String,
            eligibleTemplateIds: List<Long>,
            retrievedTemplateIds: Set<Long>,
            weeklyContextSnapshot: String,
            candidates: List<backend.yapp.core.mission.generation.port.MissionRecommendationTraceCandidate>,
        ) {
            this.weeklyContextSnapshot = weeklyContextSnapshot
        }

        override fun linkToJob(guestUserId: Long, jobId: UUID) = Unit

        override fun markShown(jobId: UUID, templateIds: Set<Long>) = Unit
    }
}
