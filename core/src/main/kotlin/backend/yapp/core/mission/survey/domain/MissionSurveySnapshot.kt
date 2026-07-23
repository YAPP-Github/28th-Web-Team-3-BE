package backend.yapp.core.mission.survey.domain

data class MissionSurveySnapshot(
    val schemaVersion: String,
    val meal: MealSurveyAnswers?,
    val transport: TransportSurveyAnswers?,
    val hobby: HobbySurveyAnswers?,
    val living: LivingSurveyAnswers?,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = "V1"
    }
}
