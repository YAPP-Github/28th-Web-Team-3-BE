package backend.yapp.api.mission.survey.dto

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.survey.domain.HobbyFrequency
import backend.yapp.core.mission.survey.domain.HobbySavingMethod
import backend.yapp.core.mission.survey.domain.HobbySpendingRange
import backend.yapp.core.mission.survey.domain.HobbySpendingType
import backend.yapp.core.mission.survey.domain.HobbySurveyAnswers
import backend.yapp.core.mission.survey.domain.HobbyType
import backend.yapp.core.mission.survey.domain.LivingArea
import backend.yapp.core.mission.survey.domain.LivingFrequency
import backend.yapp.core.mission.survey.domain.LivingSavingMethod
import backend.yapp.core.mission.survey.domain.LivingSpendingRange
import backend.yapp.core.mission.survey.domain.LivingSpendingTrigger
import backend.yapp.core.mission.survey.domain.LivingSurveyAnswers
import backend.yapp.core.mission.survey.domain.MealAlternative
import backend.yapp.core.mission.survey.domain.MealExclusion
import backend.yapp.core.mission.survey.domain.MealReason
import backend.yapp.core.mission.survey.domain.MealSurveyAnswers
import backend.yapp.core.mission.survey.domain.MealTarget
import backend.yapp.core.mission.survey.domain.MissionSurveyCode
import backend.yapp.core.mission.survey.domain.MissionSurveyReplaceCommand
import backend.yapp.core.mission.survey.domain.TransportExclusion
import backend.yapp.core.mission.survey.domain.TransportPrimaryMode
import backend.yapp.core.mission.survey.domain.TransportReason
import backend.yapp.core.mission.survey.domain.TransportSurveyAnswers
import backend.yapp.core.mission.survey.domain.TransportTarget
import backend.yapp.core.mission.survey.domain.missionSurveyCodeOf
import io.swagger.v3.oas.annotations.media.Schema

data class MissionSurveyPutRequest(
    val meal: MealSurveyRequest? = null,
    val transport: TransportSurveyRequest? = null,
    val hobby: HobbySurveyRequest? = null,
    val living: LivingSurveyRequest? = null,
) {
    fun toCommand(): MissionSurveyReplaceCommand =
        MissionSurveyReplaceCommand(
            meal = meal?.toAnswers(),
            transport = transport?.toAnswers(),
            hobby = hobby?.toAnswers(),
            living = living?.toAnswers(),
        )
}

data class MealSurveyRequest(
    @field:Schema(example = "DELIVERY")
    val target: String,
    @field:Schema(example = "3", nullable = true)
    val weeklyFrequency: Int? = null,
    val alternatives: List<String>,
    @field:Schema(example = "TIME_OR_ENERGY", nullable = true)
    val reason: String? = null,
    val exclusions: List<String>,
) {
    fun toAnswers(): MealSurveyAnswers =
        MealSurveyAnswers(
            target = parseCode(target),
            weeklyFrequency = weeklyFrequency,
            alternatives = alternatives.map(::parseCode),
            reason = reason?.let(::parseCode),
            exclusions = exclusions.map(::parseCode),
        )
}

data class TransportSurveyRequest(
    @field:Schema(example = "TAXI")
    val primaryMode: String,
    @field:Schema(example = "TAXI")
    val target: String,
    @field:Schema(example = "2", nullable = true)
    val weeklyFrequency: Int? = null,
    @field:Schema(example = "TIME_PRESSURE")
    val reason: String,
    val exclusions: List<String>,
) {
    fun toAnswers(): TransportSurveyAnswers =
        TransportSurveyAnswers(
            primaryMode = parseCode<TransportPrimaryMode>(primaryMode),
            target = parseCode<TransportTarget>(target),
            weeklyFrequency = weeklyFrequency,
            reason = parseCode<TransportReason>(reason),
            exclusions = exclusions.map(::parseCode),
        )
}

data class HobbySurveyRequest(
    val hobbies: List<String>,
    val spendingTypes: List<String>,
    @field:Schema(example = "FROM_50K_TO_150K", nullable = true)
    val monthlySpendingRange: String? = null,
    val frequencies: List<HobbyFrequencyRequest>,
    val savingMethods: List<String>,
) {
    fun toAnswers(): HobbySurveyAnswers =
        HobbySurveyAnswers(
            hobbies = hobbies.map(::parseCode),
            spendingTypes = spendingTypes.map(::parseCode),
            monthlySpendingRange = monthlySpendingRange?.let(::parseCode),
            frequencies = frequencies.map(HobbyFrequencyRequest::toDomain),
            savingMethods = savingMethods.map(::parseCode),
        )
}

data class HobbyFrequencyRequest(
    @field:Schema(example = "SUBSCRIPTION")
    val spendingType: String,
    @field:Schema(example = "2")
    val count: Int,
) {
    fun toDomain(): HobbyFrequency = HobbyFrequency(parseCode(spendingType), count)
}

data class LivingSurveyRequest(
    val areas: List<String>,
    @field:Schema(example = "FROM_30K_TO_100K", nullable = true)
    val monthlySpendingRange: String? = null,
    val frequencies: List<LivingFrequencyRequest>,
    @field:Schema(example = "DISCOUNT_OR_LIMITED_SALE")
    val trigger: String,
    val savingMethods: List<String>,
) {
    fun toAnswers(): LivingSurveyAnswers =
        LivingSurveyAnswers(
            areas = areas.map(::parseCode),
            monthlySpendingRange = monthlySpendingRange?.let(::parseCode),
            frequencies = frequencies.map(LivingFrequencyRequest::toDomain),
            trigger = parseCode<LivingSpendingTrigger>(trigger),
            savingMethods = savingMethods.map(::parseCode),
        )
}

data class LivingFrequencyRequest(
    @field:Schema(example = "SUBSCRIPTION")
    val area: String,
    @field:Schema(example = "2")
    val count: Int,
) {
    fun toDomain(): LivingFrequency = LivingFrequency(parseCode(area), count)
}

private inline fun <reified T> parseCode(value: String): T
    where T : Enum<T>, T : MissionSurveyCode =
    try {
        missionSurveyCodeOf(value)
    } catch (_: IllegalArgumentException) {
        throw BaseException(ErrorCode.MISSION_SURVEY_INVALID)
    }
