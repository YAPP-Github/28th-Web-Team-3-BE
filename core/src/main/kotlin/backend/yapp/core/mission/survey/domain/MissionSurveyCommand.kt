package backend.yapp.core.mission.survey.domain

data class MissionSurveyReplaceCommand(
    val meal: MealSurveyAnswers? = null,
    val transport: TransportSurveyAnswers? = null,
    val hobby: HobbySurveyAnswers? = null,
    val living: LivingSurveyAnswers? = null,
)

data class MealSurveyAnswers(
    val target: MealTarget,
    val weeklyFrequency: Int?,
    val alternatives: List<MealAlternative>,
    val reason: MealReason?,
    val exclusions: List<MealExclusion>,
)

data class TransportSurveyAnswers(
    val primaryMode: TransportPrimaryMode,
    val target: TransportTarget,
    val weeklyFrequency: Int?,
    val reason: TransportReason,
    val exclusions: List<TransportExclusion>,
)

data class HobbySurveyAnswers(
    val hobbies: List<HobbyType>,
    val spendingTypes: List<HobbySpendingType>,
    val monthlySpendingRange: HobbySpendingRange?,
    val frequencies: List<HobbyFrequency>,
    val savingMethods: List<HobbySavingMethod>,
    val otherHobby: String? = null,
) {
    companion object {
        const val MAX_OTHER_HOBBY_LENGTH = 50
    }
}

data class HobbyFrequency(
    val spendingType: HobbySpendingType,
    val count: Int,
)

data class LivingSurveyAnswers(
    val areas: List<LivingArea>,
    val monthlySpendingRange: LivingSpendingRange?,
    val frequencies: List<LivingFrequency>,
    val trigger: LivingSpendingTrigger,
    val savingMethods: List<LivingSavingMethod>,
)

data class LivingFrequency(
    val area: LivingArea,
    val count: Int,
)
