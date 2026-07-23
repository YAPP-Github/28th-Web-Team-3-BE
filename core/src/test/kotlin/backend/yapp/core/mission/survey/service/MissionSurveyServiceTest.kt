package backend.yapp.core.mission.survey.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.survey.domain.MealAlternative
import backend.yapp.core.mission.survey.domain.MealExclusion
import backend.yapp.core.mission.survey.domain.MealReason
import backend.yapp.core.mission.survey.domain.MealSurveyAnswers
import backend.yapp.core.mission.survey.domain.MealTarget
import backend.yapp.core.mission.survey.domain.MissionSurvey
import backend.yapp.core.mission.survey.domain.MissionSurveyAnswerValue
import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionCatalog
import backend.yapp.core.mission.survey.domain.MissionSurveyReplaceCommand
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.mission.survey.domain.MissionSurveyValidator
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

class MissionSurveyServiceTest {
    @Test
    fun `optimistic locking failure is mapped to mission survey conflict`() {
        assertConflict(OptimisticLockingFailureException("concurrent update"))
    }

    @Test
    fun `data integrity violation is mapped to mission survey conflict`() {
        assertConflict(DataIntegrityViolationException("duplicate guest survey"))
    }

    @Test
    fun `decode failure preserves its original cause`() {
        val repository = mock(MissionSurveyRepository::class.java)
        val survey = MissionSurvey(guestUserId = GUEST_USER_ID, id = 1L)
        survey.addAnswers(
            listOf(
                MissionSurveyAnswerValue(
                    categoryCode = "MEAL",
                    questionCode = "MEAL_TARGET",
                    valueType = "OPTION",
                    answerCode = "UNSUPPORTED",
                ),
            ),
            Instant.EPOCH,
        )
        `when`(repository.findByGuestUserId(GUEST_USER_ID)).thenReturn(survey)
        val service = MissionSurveyService(
            repository = repository,
            validator = MissionSurveyValidator(MissionSurveyQuestionCatalog()),
            questionCatalog = MissionSurveyQuestionCatalog(),
        )

        val exception = assertFailsWith<BaseException> {
            service.get(GUEST_USER_ID)
        }

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.errorCode)
        assertIs<IllegalArgumentException>(exception.cause)
    }

    private fun assertConflict(repositoryFailure: RuntimeException) {
        val repository = mock(MissionSurveyRepository::class.java)
        `when`(repository.findByGuestUserId(GUEST_USER_ID)).thenReturn(null)
        `when`(repository.saveAndFlush(any(MissionSurvey::class.java))).thenThrow(repositoryFailure)
        val service = MissionSurveyService(
            repository = repository,
            validator = MissionSurveyValidator(MissionSurveyQuestionCatalog()),
            questionCatalog = MissionSurveyQuestionCatalog(),
        )

        val exception = assertFailsWith<BaseException> {
            service.replace(GUEST_USER_ID, validCommand())
        }

        assertEquals(ErrorCode.MISSION_SURVEY_CONFLICT, exception.errorCode)
    }

    private fun validCommand() =
        MissionSurveyReplaceCommand(
            meal = MealSurveyAnswers(
                target = MealTarget.DELIVERY,
                weeklyFrequency = 3,
                alternatives = listOf(MealAlternative.COOK),
                reason = MealReason.TIME_OR_ENERGY,
                exclusions = listOf(MealExclusion.NONE),
            ),
        )

    companion object {
        private const val GUEST_USER_ID = 1L
    }
}
