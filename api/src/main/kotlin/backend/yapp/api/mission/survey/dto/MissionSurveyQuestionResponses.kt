package backend.yapp.api.mission.survey.dto

import backend.yapp.core.mission.survey.domain.MissionSurveyCategoryQuestions
import backend.yapp.core.mission.survey.domain.MissionSurveyConditionalOptionRule
import backend.yapp.core.mission.survey.domain.MissionSurveyNumericRule
import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionDefinition
import backend.yapp.core.mission.survey.domain.MissionSurveyTextRule
import backend.yapp.core.mission.survey.domain.MissionSurveyFrequencyRangeOption
import backend.yapp.core.mission.survey.domain.MissionSurveyFrequencyRangeRule

data class MissionSurveyQuestionsResponse(
    val categories: List<MissionSurveyCategoryQuestionsResponse>,
) {
    companion object {
        fun from(categories: List<MissionSurveyCategoryQuestions>): MissionSurveyQuestionsResponse =
            MissionSurveyQuestionsResponse(categories.map(MissionSurveyCategoryQuestionsResponse::from))
    }
}

data class MissionSurveyCategoryQuestionsResponse(
    val category: String,
    val questions: List<MissionSurveyQuestionResponse>,
) {
    companion object {
        fun from(category: MissionSurveyCategoryQuestions): MissionSurveyCategoryQuestionsResponse =
            MissionSurveyCategoryQuestionsResponse(
                category = category.category.code,
                questions = category.questions.map(MissionSurveyQuestionResponse::from),
            )
    }
}

data class MissionSurveyQuestionResponse(
    val code: String,
    val prompt: String,
    val answerType: String,
    val options: List<MissionSurveyOptionResponse>,
    val minSelections: Int?,
    val maxSelections: Int?,
    val dependsOnQuestionCode: String?,
    val skipWhenOptionCodes: List<String>,
    val numericRules: List<MissionSurveyNumericRuleResponse>,
    val frequencyRangeOptions: List<MissionSurveyFrequencyRangeOptionResponse>,
    val frequencyRangeRules: List<MissionSurveyFrequencyRangeRuleResponse>,
    val textRules: List<MissionSurveyTextRuleResponse>,
    val exclusiveOptionCodes: List<String>,
    val conditionalOptionRules: List<MissionSurveyConditionalOptionRuleResponse>,
    val impacts: List<String>,
) {
    companion object {
        fun from(question: MissionSurveyQuestionDefinition): MissionSurveyQuestionResponse =
            MissionSurveyQuestionResponse(
                code = question.code,
                prompt = question.prompt,
                answerType = question.answerType.code,
                options = question.options.map { MissionSurveyOptionResponse(it.code, it.label) },
                minSelections = question.minSelections,
                maxSelections = question.maxSelections,
                dependsOnQuestionCode = question.dependsOnQuestionCode,
                skipWhenOptionCodes = question.skipWhenOptionCodes,
                numericRules = question.numericRules.map(MissionSurveyNumericRuleResponse::from),
                frequencyRangeOptions = question.frequencyRangeOptions.map(MissionSurveyFrequencyRangeOptionResponse::from),
                frequencyRangeRules = question.frequencyRangeRules.map(MissionSurveyFrequencyRangeRuleResponse::from),
                textRules = question.textRules.map(MissionSurveyTextRuleResponse::from),
                exclusiveOptionCodes = question.exclusiveOptionCodes,
                conditionalOptionRules = question.conditionalOptionRules
                    .map(MissionSurveyConditionalOptionRuleResponse::from),
                impacts = question.impacts.map { it.code },
            )
    }
}

data class MissionSurveyFrequencyRangeRuleResponse(
    val subjectOptionCode: String,
    val unit: String,
    val options: List<MissionSurveyFrequencyRangeOptionResponse>,
) {
    companion object {
        fun from(rule: MissionSurveyFrequencyRangeRule): MissionSurveyFrequencyRangeRuleResponse =
            MissionSurveyFrequencyRangeRuleResponse(
                rule.subjectOptionCode,
                rule.unit.code,
                rule.options.map(MissionSurveyFrequencyRangeOptionResponse::from),
            )
    }
}

data class MissionSurveyFrequencyRangeOptionResponse(
    val code: String,
    val label: String,
    val minimum: Int,
    val maximum: Int?,
) {
    companion object {
        fun from(option: MissionSurveyFrequencyRangeOption): MissionSurveyFrequencyRangeOptionResponse =
            MissionSurveyFrequencyRangeOptionResponse(option.code, option.label, option.minimum, option.maximum)
    }
}

data class MissionSurveyOptionResponse(
    val code: String,
    val label: String,
)

data class MissionSurveyNumericRuleResponse(
    val subjectOptionCode: String,
    val unit: String,
    val minimum: Int,
    val maximum: Int,
) {
    companion object {
        fun from(rule: MissionSurveyNumericRule): MissionSurveyNumericRuleResponse =
            MissionSurveyNumericRuleResponse(
                subjectOptionCode = rule.subjectOptionCode,
                unit = rule.unit.code,
                minimum = rule.minimum,
                maximum = rule.maximum,
            )
    }
}

data class MissionSurveyTextRuleResponse(
    val subjectOptionCode: String,
    val minimumLength: Int,
    val maximumLength: Int,
) {
    companion object {
        fun from(rule: MissionSurveyTextRule): MissionSurveyTextRuleResponse =
            MissionSurveyTextRuleResponse(
                subjectOptionCode = rule.subjectOptionCode,
                minimumLength = rule.minimumLength,
                maximumLength = rule.maximumLength,
            )
    }
}

data class MissionSurveyConditionalOptionRuleResponse(
    val dependsOnQuestionCode: String,
    val whenOptionCodes: List<String>,
    val allowedOptionCodes: List<String>,
) {
    companion object {
        fun from(rule: MissionSurveyConditionalOptionRule): MissionSurveyConditionalOptionRuleResponse =
            MissionSurveyConditionalOptionRuleResponse(
                dependsOnQuestionCode = rule.dependsOnQuestionCode,
                whenOptionCodes = rule.whenOptionCodes,
                allowedOptionCodes = rule.allowedOptionCodes,
            )
    }
}
