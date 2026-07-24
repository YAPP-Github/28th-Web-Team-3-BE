package backend.yapp.core.mission.survey.domain

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class MissionSurveyValidator(
    private val questionCatalog: MissionSurveyQuestionCatalog,
) {
    fun validate(command: MissionSurveyReplaceCommand) {
        validate(command, allowLegacyMissingOtherHobby = false)
    }

    fun validateStored(command: MissionSurveyReplaceCommand) {
        validate(command, allowLegacyMissingOtherHobby = true)
    }

    private fun validate(
        command: MissionSurveyReplaceCommand,
        allowLegacyMissingOtherHobby: Boolean,
    ) {
        val categoryCount = listOf(command.meal, command.transport, command.hobby, command.living).count { it != null }
        requireValid(categoryCount in 1..4)
        command.meal?.let(::validateMeal)
        command.transport?.let(::validateTransport)
        command.hobby?.let { validateHobby(it, allowLegacyMissingOtherHobby) }
        command.living?.let(::validateLiving)
    }

    private fun validateMeal(answers: MealSurveyAnswers) {
        requireSelections(answers.alternatives, MissionSurveyQuestionCode.MEAL_ALTERNATIVES)
        requireSelections(answers.exclusions, MissionSurveyQuestionCode.MEAL_EXCLUSIONS)

        if (answers.target == MealTarget.UNKNOWN) {
            requireValid(answers.weeklyFrequency == null && answers.reason == null)
        } else {
            requireValid(answers.weeklyFrequency != null)
            requireValid(answers.reason != null)
        }
    }

    private fun validateTransport(answers: TransportSurveyAnswers) {
        requireSelections(answers.exclusions, MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS)
        if (answers.target == TransportTarget.UNKNOWN) {
            requireValid(answers.weeklyFrequency == null)
        } else {
            requireValid(answers.weeklyFrequency != null)
        }
    }

    private fun validateHobby(
        answers: HobbySurveyAnswers,
        allowLegacyMissingOtherHobby: Boolean,
    ) {
        requireSelections(answers.hobbies, MissionSurveyQuestionCode.HOBBY_TYPES)
        requireSelections(answers.spendingTypes, MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES)
        requireSelections(answers.savingMethods, MissionSurveyQuestionCode.HOBBY_SAVING_METHODS)
        requireOtherHobby(answers, allowLegacyMissingOtherHobby)
        requireConditionalOptions(
            answers.savingMethods,
            MissionSurveyQuestionCode.HOBBY_SAVING_METHODS,
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES to answers.spendingTypes,
        )

        if (answers.spendingTypes == listOf(HobbySpendingType.DO_NOT_REDUCE)) {
            requireValid(answers.monthlySpendingRange == null)
            requireValid(answers.frequencies.isEmpty())
            requireValid(answers.savingMethods == listOf(HobbySavingMethod.NO_HOBBY_MISSION))
            return
        }

        requireValid(answers.monthlySpendingRange != null)
        requireValid(answers.frequencies.map { it.spendingType }.toSet() == answers.spendingTypes.toSet())
        requireValid(answers.frequencies.size == answers.spendingTypes.size)
        answers.frequencies.forEach {
            requireValid(it.spendingType != HobbySpendingType.DO_NOT_REDUCE)
            requireFrequencyRange(it.range, unitFor(it.spendingType))
        }
    }

    private fun requireOtherHobby(
        answers: HobbySurveyAnswers,
        allowLegacyMissingOtherHobby: Boolean,
    ) {
        val textRule = questionCatalog.question(MissionSurveyQuestionCode.HOBBY_TYPES).textRules
            .single { it.subjectOptionCode == HobbyType.OTHER.code }
        val hasOther = HobbyType.OTHER in answers.hobbies
        val otherHobby = answers.otherHobby

        if (!hasOther) {
            requireValid(otherHobby == null)
            return
        }
        if (allowLegacyMissingOtherHobby && otherHobby == null) return

        requireValid(
            otherHobby != null &&
                otherHobby == otherHobby.trim() &&
                otherHobby.length in textRule.minimumLength..textRule.maximumLength,
        )
    }

    private fun validateLiving(answers: LivingSurveyAnswers) {
        requireSelections(answers.areas, MissionSurveyQuestionCode.LIVING_AREAS)
        requireSelections(answers.savingMethods, MissionSurveyQuestionCode.LIVING_SAVING_METHODS)

        if (answers.areas == listOf(LivingArea.UNKNOWN)) {
            requireValid(answers.monthlySpendingRange == null)
            requireValid(answers.frequencies.isEmpty())
            return
        }

        requireValid(answers.monthlySpendingRange != null)
        requireValid(answers.frequencies.map { it.area }.toSet() == answers.areas.toSet())
        requireValid(answers.frequencies.size == answers.areas.size)
        answers.frequencies.forEach {
            requireValid(it.area != LivingArea.UNKNOWN)
            requireFrequencyRange(it.range, unitFor(it.area))
        }
    }

    private fun <T> requireSelections(
        values: List<T>,
        questionCode: MissionSurveyQuestionCode,
    ) where T : MissionSurveyCode {
        val question = questionCatalog.question(questionCode)
        val minimum = checkNotNull(question.minSelections)
        val maximum = checkNotNull(question.maxSelections)
        val codes = values.map(MissionSurveyCode::code)
        requireValid(codes.size in minimum..maximum && codes.distinct().size == codes.size)
        requireValid(question.exclusiveOptionCodes.none(codes::contains) || codes.size == 1)
    }

    private fun requireNumeric(
        questionCode: MissionSurveyQuestionCode,
        subject: MissionSurveyCode,
        value: Int,
    ) {
        val rule = questionCatalog.question(questionCode).numericRules
            .singleOrNull { it.subjectOptionCode == subject.code }
        requireValid(rule != null && value in rule.minimum..rule.maximum)
    }

    private fun requireFrequencyRange(
        range: SurveyFrequencyRange,
        unit: SurveyFrequencyUnit,
    ) {
        requireValid(
            when (unit) {
                SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS -> range is FourWeeklyFrequencyRange
                SurveyFrequencyUnit.SUBSCRIPTION_COUNT -> range is SubscriptionCountRange
                else -> false
            },
        )
    }

    private fun unitFor(type: HobbySpendingType): SurveyFrequencyUnit =
        if (type == HobbySpendingType.SUBSCRIPTION) SurveyFrequencyUnit.SUBSCRIPTION_COUNT else SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS

    private fun unitFor(area: LivingArea): SurveyFrequencyUnit =
        if (area == LivingArea.SUBSCRIPTION) SurveyFrequencyUnit.SUBSCRIPTION_COUNT else SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS

    private fun <T, D> requireConditionalOptions(
        values: List<T>,
        questionCode: MissionSurveyQuestionCode,
        dependency: Pair<MissionSurveyQuestionCode, List<D>>,
    ) where T : MissionSurveyCode, D : MissionSurveyCode {
        val rules = questionCatalog.question(questionCode).conditionalOptionRules
        if (rules.isEmpty()) return

        val selectedCodes = values.map(MissionSurveyCode::code)
        val dependencyCodes = dependency.second.map(MissionSurveyCode::code)
        val matchingRule = rules.singleOrNull {
            it.dependsOnQuestionCode == dependency.first.code &&
                dependencyCodes.containsAll(it.whenOptionCodes)
        }

        if (matchingRule != null) {
            requireValid(selectedCodes.all(matchingRule.allowedOptionCodes::contains))
        }
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw BaseException(ErrorCode.MISSION_SURVEY_INVALID)
    }

    private fun <T : Any> requireValue(value: T?): T {
        requireValid(value != null)
        return checkNotNull(value)
    }
}
