package backend.yapp.core.mission.survey.domain

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class MissionSurveyValidator {
    fun validate(command: MissionSurveyReplaceCommand) {
        val categoryCount = listOf(command.meal, command.transport, command.hobby, command.living).count { it != null }
        requireValid(categoryCount in 1..4)
        command.meal?.let(::validateMeal)
        command.transport?.let(::validateTransport)
        command.hobby?.let(::validateHobby)
        command.living?.let(::validateLiving)
    }

    private fun validateMeal(answers: MealSurveyAnswers) {
        requireDistinctSize(answers.alternatives, 1..MealAlternative.entries.size)
        requireDistinctSize(answers.exclusions, 1..MealExclusion.entries.size)
        requireExclusive(answers.alternatives, MealAlternative.NO_ALTERNATIVE)
        requireExclusive(answers.exclusions, MealExclusion.NONE)

        if (answers.target == MealTarget.UNKNOWN) {
            requireValid(answers.weeklyFrequency == null && answers.reason == null)
        } else {
            val maximum = if (answers.target == MealTarget.PAID_BEVERAGE) 14 else 7
            requireValid(answers.weeklyFrequency != null && answers.weeklyFrequency in 0..maximum)
            requireValid(answers.reason != null)
        }
    }

    private fun validateTransport(answers: TransportSurveyAnswers) {
        requireDistinctSize(answers.exclusions, 1..TransportExclusion.entries.size)
        requireExclusive(answers.exclusions, TransportExclusion.NONE)
        if (answers.target == TransportTarget.UNKNOWN) {
            requireValid(answers.weeklyFrequency == null)
        } else {
            requireValid(answers.weeklyFrequency != null && answers.weeklyFrequency in 0..7)
        }
    }

    private fun validateHobby(answers: HobbySurveyAnswers) {
        requireDistinctSize(answers.hobbies, 1..HobbyType.entries.size)
        requireDistinctSize(answers.spendingTypes, 1..2)
        requireDistinctSize(answers.savingMethods, 1..HobbySavingMethod.entries.size)
        requireExclusive(answers.spendingTypes, HobbySpendingType.DO_NOT_REDUCE)
        requireExclusive(answers.savingMethods, HobbySavingMethod.NO_HOBBY_MISSION)

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
            requireValid(it.count in 0..maximumFor(it.spendingType))
        }
    }

    private fun validateLiving(answers: LivingSurveyAnswers) {
        requireDistinctSize(answers.areas, 1..2)
        requireDistinctSize(answers.savingMethods, 1..LivingSavingMethod.entries.size)
        requireExclusive(answers.areas, LivingArea.UNKNOWN)
        requireExclusive(answers.savingMethods, LivingSavingMethod.NO_LIVING_MISSION)

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
            requireValid(it.count in 0..maximumFor(it.area))
        }
    }

    private fun maximumFor(type: HobbySpendingType): Int =
        if (type == HobbySpendingType.SUBSCRIPTION) 20 else 31

    private fun maximumFor(area: LivingArea): Int =
        if (area == LivingArea.SUBSCRIPTION) 20 else 31

    private fun <T> requireDistinctSize(values: List<T>, range: IntRange) {
        requireValid(values.size in range && values.distinct().size == values.size)
    }

    private fun <T> requireExclusive(values: List<T>, exclusive: T) {
        requireValid(exclusive !in values || values.size == 1)
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw BaseException(ErrorCode.MISSION_SURVEY_INVALID)
    }
}
