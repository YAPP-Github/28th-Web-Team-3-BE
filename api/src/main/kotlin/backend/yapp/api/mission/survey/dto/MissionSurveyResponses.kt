package backend.yapp.api.mission.survey.dto

import backend.yapp.core.mission.survey.domain.HobbySurveyAnswers
import backend.yapp.core.mission.survey.domain.LivingSurveyAnswers
import backend.yapp.core.mission.survey.domain.MealSurveyAnswers
import backend.yapp.core.mission.survey.domain.MissionSurveySnapshot
import backend.yapp.core.mission.survey.domain.TransportSurveyAnswers

data class MissionSurveyResponse(
    val schemaVersion: String,
    val meal: MealSurveyResponse?,
    val transport: TransportSurveyResponse?,
    val hobby: HobbySurveyResponse?,
    val living: LivingSurveyResponse?,
) {
    companion object {
        fun from(snapshot: MissionSurveySnapshot): MissionSurveyResponse =
            MissionSurveyResponse(
                schemaVersion = snapshot.schemaVersion,
                meal = snapshot.meal?.let(MealSurveyResponse::from),
                transport = snapshot.transport?.let(TransportSurveyResponse::from),
                hobby = snapshot.hobby?.let(HobbySurveyResponse::from),
                living = snapshot.living?.let(LivingSurveyResponse::from),
            )
    }
}

data class MealSurveyResponse(
    val target: String,
    val weeklyFrequencyRange: String?,
    val alternatives: List<String>,
    val reason: String?,
    val exclusions: List<String>,
) {
    companion object {
        fun from(answers: MealSurveyAnswers): MealSurveyResponse =
            MealSurveyResponse(
                target = answers.target.code,
                weeklyFrequencyRange = answers.weeklyFrequency?.code,
                alternatives = answers.alternatives.map { it.code },
                reason = answers.reason?.code,
                exclusions = answers.exclusions.map { it.code },
            )
    }
}

data class TransportSurveyResponse(
    val primaryMode: String,
    val target: String,
    val weeklyFrequencyRange: String?,
    val reason: String,
    val exclusions: List<String>,
) {
    companion object {
        fun from(answers: TransportSurveyAnswers): TransportSurveyResponse =
            TransportSurveyResponse(
                primaryMode = answers.primaryMode.code,
                target = answers.target.code,
                weeklyFrequencyRange = answers.weeklyFrequency?.code,
                reason = answers.reason.code,
                exclusions = answers.exclusions.map { it.code },
            )
    }
}

data class HobbySurveyResponse(
    val hobbies: List<String>,
    val otherHobby: String?,
    val spendingTypes: List<String>,
    val monthlySpendingRange: String?,
    val frequencies: List<HobbyFrequencyResponse>,
    val savingMethods: List<String>,
) {
    companion object {
        fun from(answers: HobbySurveyAnswers): HobbySurveyResponse =
            HobbySurveyResponse(
                hobbies = answers.hobbies.map { it.code },
                otherHobby = answers.otherHobby,
                spendingTypes = answers.spendingTypes.map { it.code },
                monthlySpendingRange = answers.monthlySpendingRange?.code,
                frequencies = answers.frequencies.map { HobbyFrequencyResponse(it.spendingType.code, it.count) },
                savingMethods = answers.savingMethods.map { it.code },
            )
    }
}

data class HobbyFrequencyResponse(
    val spendingType: String,
    val count: Int,
)

data class LivingSurveyResponse(
    val areas: List<String>,
    val monthlySpendingRange: String?,
    val frequencies: List<LivingFrequencyResponse>,
    val trigger: String,
    val savingMethods: List<String>,
) {
    companion object {
        fun from(answers: LivingSurveyAnswers): LivingSurveyResponse =
            LivingSurveyResponse(
                areas = answers.areas.map { it.code },
                monthlySpendingRange = answers.monthlySpendingRange?.code,
                frequencies = answers.frequencies.map { LivingFrequencyResponse(it.area.code, it.count) },
                trigger = answers.trigger.code,
                savingMethods = answers.savingMethods.map { it.code },
            )
    }
}

data class LivingFrequencyResponse(
    val area: String,
    val count: Int,
)
