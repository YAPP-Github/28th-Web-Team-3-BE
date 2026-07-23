package backend.yapp.core.mission.survey.service

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
import backend.yapp.core.mission.survey.domain.MissionSurvey
import backend.yapp.core.mission.survey.domain.MissionSurveyAnswer
import backend.yapp.core.mission.survey.domain.MissionSurveyAnswerValue
import backend.yapp.core.mission.survey.domain.MissionSurveyCategory
import backend.yapp.core.mission.survey.domain.MissionSurveyCategoryQuestions
import backend.yapp.core.mission.survey.domain.MissionSurveyCode
import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionCatalog
import backend.yapp.core.mission.survey.domain.MissionSurveyQuestionCode
import backend.yapp.core.mission.survey.domain.MissionSurveyReplaceCommand
import backend.yapp.core.mission.survey.domain.MissionSurveyRepository
import backend.yapp.core.mission.survey.domain.MissionSurveySnapshot
import backend.yapp.core.mission.survey.domain.MissionSurveyValidator
import backend.yapp.core.mission.survey.domain.SurveyFrequencyUnit
import backend.yapp.core.mission.survey.domain.TransportExclusion
import backend.yapp.core.mission.survey.domain.TransportPrimaryMode
import backend.yapp.core.mission.survey.domain.TransportReason
import backend.yapp.core.mission.survey.domain.TransportSurveyAnswers
import backend.yapp.core.mission.survey.domain.TransportTarget
import backend.yapp.core.mission.survey.domain.missionSurveyCodeOf
import java.time.Clock
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionSurveyService(
    private val repository: MissionSurveyRepository,
    private val validator: MissionSurveyValidator,
    private val questionCatalog: MissionSurveyQuestionCatalog,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun questions(rawCategories: List<String>): List<MissionSurveyCategoryQuestions> =
        questionCatalog.questions(questionCatalog.categories(rawCategories))

    @Transactional(readOnly = true)
    fun get(guestUserId: Long): MissionSurveySnapshot {
        val survey = repository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.MISSION_SURVEY_NOT_FOUND)
        return decode(survey)
    }

    @Transactional
    fun replace(guestUserId: Long, command: MissionSurveyReplaceCommand): MissionSurveySnapshot {
        validator.validate(command)
        val values = encode(command)
        val survey = repository.findByGuestUserId(guestUserId)
            ?: MissionSurvey(guestUserId = guestUserId, createdAt = clock.instant(), updatedAt = clock.instant())

        val saved = try {
            survey.clearAnswers(clock.instant())
            if (survey.id != 0L) repository.flush()
            survey.addAnswers(values, clock.instant())
            repository.saveAndFlush(survey)
        } catch (_: OptimisticLockingFailureException) {
            throw BaseException(ErrorCode.MISSION_SURVEY_CONFLICT)
        } catch (_: DataIntegrityViolationException) {
            throw BaseException(ErrorCode.MISSION_SURVEY_CONFLICT)
        }
        return decode(saved)
    }

    private fun encode(command: MissionSurveyReplaceCommand): List<MissionSurveyAnswerValue> = buildList {
        command.meal?.let { addMeal(it) }
        command.transport?.let { addTransport(it) }
        command.hobby?.let { addHobby(it) }
        command.living?.let { addLiving(it) }
    }

    private fun MutableList<MissionSurveyAnswerValue>.addMeal(answers: MealSurveyAnswers) {
        option(MissionSurveyCategory.MEAL, MissionSurveyQuestionCode.MEAL_TARGET, answers.target)
        answers.weeklyFrequency?.let {
            number(
                MissionSurveyCategory.MEAL,
                MissionSurveyQuestionCode.MEAL_FREQUENCY,
                answers.target,
                it,
                SurveyFrequencyUnit.TIMES_PER_WEEK,
            )
        }
        options(MissionSurveyCategory.MEAL, MissionSurveyQuestionCode.MEAL_ALTERNATIVES, answers.alternatives)
        answers.reason?.let { option(MissionSurveyCategory.MEAL, MissionSurveyQuestionCode.MEAL_REASON, it) }
        options(MissionSurveyCategory.MEAL, MissionSurveyQuestionCode.MEAL_EXCLUSIONS, answers.exclusions)
    }

    private fun MutableList<MissionSurveyAnswerValue>.addTransport(answers: TransportSurveyAnswers) {
        option(
            MissionSurveyCategory.TRANSPORT,
            MissionSurveyQuestionCode.TRANSPORT_PRIMARY_MODE,
            answers.primaryMode,
        )
        option(MissionSurveyCategory.TRANSPORT, MissionSurveyQuestionCode.TRANSPORT_TARGET, answers.target)
        answers.weeklyFrequency?.let {
            number(
                MissionSurveyCategory.TRANSPORT,
                MissionSurveyQuestionCode.TRANSPORT_FREQUENCY,
                answers.target,
                it,
                SurveyFrequencyUnit.TIMES_PER_WEEK,
            )
        }
        option(MissionSurveyCategory.TRANSPORT, MissionSurveyQuestionCode.TRANSPORT_REASON, answers.reason)
        options(
            MissionSurveyCategory.TRANSPORT,
            MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS,
            answers.exclusions,
        )
    }

    private fun MutableList<MissionSurveyAnswerValue>.addHobby(answers: HobbySurveyAnswers) {
        options(MissionSurveyCategory.HOBBY, MissionSurveyQuestionCode.HOBBY_TYPES, answers.hobbies)
        options(
            MissionSurveyCategory.HOBBY,
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES,
            answers.spendingTypes,
        )
        answers.monthlySpendingRange?.let {
            option(MissionSurveyCategory.HOBBY, MissionSurveyQuestionCode.HOBBY_MONTHLY_SPENDING, it)
        }
        answers.frequencies.forEach {
            number(
                MissionSurveyCategory.HOBBY,
                MissionSurveyQuestionCode.HOBBY_FREQUENCIES,
                it.spendingType,
                it.count,
                unitFor(it.spendingType),
            )
        }
        options(
            MissionSurveyCategory.HOBBY,
            MissionSurveyQuestionCode.HOBBY_SAVING_METHODS,
            answers.savingMethods,
        )
    }

    private fun MutableList<MissionSurveyAnswerValue>.addLiving(answers: LivingSurveyAnswers) {
        options(MissionSurveyCategory.LIVING, MissionSurveyQuestionCode.LIVING_AREAS, answers.areas)
        answers.monthlySpendingRange?.let {
            option(MissionSurveyCategory.LIVING, MissionSurveyQuestionCode.LIVING_MONTHLY_SPENDING, it)
        }
        answers.frequencies.forEach {
            number(
                MissionSurveyCategory.LIVING,
                MissionSurveyQuestionCode.LIVING_FREQUENCIES,
                it.area,
                it.count,
                unitFor(it.area),
            )
        }
        option(MissionSurveyCategory.LIVING, MissionSurveyQuestionCode.LIVING_TRIGGER, answers.trigger)
        options(
            MissionSurveyCategory.LIVING,
            MissionSurveyQuestionCode.LIVING_SAVING_METHODS,
            answers.savingMethods,
        )
    }

    private fun MutableList<MissionSurveyAnswerValue>.option(
        category: MissionSurveyCategory,
        question: MissionSurveyQuestionCode,
        answer: MissionSurveyCode,
    ) {
        add(
            MissionSurveyAnswerValue(
                categoryCode = category.code,
                questionCode = question.code,
                valueType = OPTION_VALUE_TYPE,
                answerCode = answer.code,
            ),
        )
    }

    private fun MutableList<MissionSurveyAnswerValue>.options(
        category: MissionSurveyCategory,
        question: MissionSurveyQuestionCode,
        answers: List<MissionSurveyCode>,
    ) {
        answers.forEach { option(category, question, it) }
    }

    private fun MutableList<MissionSurveyAnswerValue>.number(
        category: MissionSurveyCategory,
        question: MissionSurveyQuestionCode,
        subject: MissionSurveyCode?,
        value: Int,
        unit: SurveyFrequencyUnit,
    ) {
        add(
            MissionSurveyAnswerValue(
                categoryCode = category.code,
                questionCode = question.code,
                valueType = NUMBER_VALUE_TYPE,
                answerCode = subject?.code ?: SELF_ANSWER_CODE,
                numericValue = value,
                unitCode = unit.code,
            ),
        )
    }

    private fun decode(survey: MissionSurvey): MissionSurveySnapshot {
        if (survey.schemaVersion != MissionSurveySnapshot.CURRENT_SCHEMA_VERSION) internal()
        val persisted = survey.answerRows().map { it.toValue() }
        val command = try {
            MissionSurveyReplaceCommand(
                meal = decodeMeal(persisted),
                transport = decodeTransport(persisted),
                hobby = decodeHobby(persisted),
                living = decodeLiving(persisted),
            ).also(validator::validate)
        } catch (_: Exception) {
            internal()
        }

        val canonicalValues = encode(command)
        if (persisted.size != canonicalValues.size || persisted.toSet() != canonicalValues.toSet()) internal()
        return MissionSurveySnapshot(
            schemaVersion = survey.schemaVersion,
            meal = command.meal,
            transport = command.transport,
            hobby = command.hobby,
            living = command.living,
        )
    }

    private fun decodeMeal(rows: List<MissionSurveyAnswerValue>): MealSurveyAnswers? {
        val reader = CategoryRows(rows, MissionSurveyCategory.MEAL)
        if (reader.isEmpty()) return null
        val target = reader.single<MealTarget>(MissionSurveyQuestionCode.MEAL_TARGET)
        return MealSurveyAnswers(
            target = target,
            weeklyFrequency = if (target == MealTarget.UNKNOWN) {
                null
            } else {
                reader.number(MissionSurveyQuestionCode.MEAL_FREQUENCY, target, SurveyFrequencyUnit.TIMES_PER_WEEK)
            },
            alternatives = reader.many(MissionSurveyQuestionCode.MEAL_ALTERNATIVES),
            reason = if (target == MealTarget.UNKNOWN) null else reader.single(MissionSurveyQuestionCode.MEAL_REASON),
            exclusions = reader.many(MissionSurveyQuestionCode.MEAL_EXCLUSIONS),
        )
    }

    private fun decodeTransport(rows: List<MissionSurveyAnswerValue>): TransportSurveyAnswers? {
        val reader = CategoryRows(rows, MissionSurveyCategory.TRANSPORT)
        if (reader.isEmpty()) return null
        val target = reader.single<TransportTarget>(MissionSurveyQuestionCode.TRANSPORT_TARGET)
        return TransportSurveyAnswers(
            primaryMode = reader.single(MissionSurveyQuestionCode.TRANSPORT_PRIMARY_MODE),
            target = target,
            weeklyFrequency = if (target == TransportTarget.UNKNOWN) {
                null
            } else {
                reader.number(
                    MissionSurveyQuestionCode.TRANSPORT_FREQUENCY,
                    target,
                    SurveyFrequencyUnit.TIMES_PER_WEEK,
                )
            },
            reason = reader.single(MissionSurveyQuestionCode.TRANSPORT_REASON),
            exclusions = reader.many(MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS),
        )
    }

    private fun decodeHobby(rows: List<MissionSurveyAnswerValue>): HobbySurveyAnswers? {
        val reader = CategoryRows(rows, MissionSurveyCategory.HOBBY)
        if (reader.isEmpty()) return null
        val spendingTypes = reader.many<HobbySpendingType>(MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES)
        val noReduction = spendingTypes == listOf(HobbySpendingType.DO_NOT_REDUCE)
        return HobbySurveyAnswers(
            hobbies = reader.many(MissionSurveyQuestionCode.HOBBY_TYPES),
            spendingTypes = spendingTypes,
            monthlySpendingRange = if (noReduction) {
                null
            } else {
                reader.single(MissionSurveyQuestionCode.HOBBY_MONTHLY_SPENDING)
            },
            frequencies = if (noReduction) {
                emptyList()
            } else {
                spendingTypes.map {
                    HobbyFrequency(
                        it,
                        reader.number(MissionSurveyQuestionCode.HOBBY_FREQUENCIES, it, unitFor(it)),
                    )
                }
            },
            savingMethods = reader.many(MissionSurveyQuestionCode.HOBBY_SAVING_METHODS),
        )
    }

    private fun decodeLiving(rows: List<MissionSurveyAnswerValue>): LivingSurveyAnswers? {
        val reader = CategoryRows(rows, MissionSurveyCategory.LIVING)
        if (reader.isEmpty()) return null
        val areas = reader.many<LivingArea>(MissionSurveyQuestionCode.LIVING_AREAS)
        val unknown = areas == listOf(LivingArea.UNKNOWN)
        return LivingSurveyAnswers(
            areas = areas,
            monthlySpendingRange = if (unknown) {
                null
            } else {
                reader.single(MissionSurveyQuestionCode.LIVING_MONTHLY_SPENDING)
            },
            frequencies = if (unknown) {
                emptyList()
            } else {
                areas.map {
                    LivingFrequency(
                        it,
                        reader.number(MissionSurveyQuestionCode.LIVING_FREQUENCIES, it, unitFor(it)),
                    )
                }
            },
            trigger = reader.single(MissionSurveyQuestionCode.LIVING_TRIGGER),
            savingMethods = reader.many(MissionSurveyQuestionCode.LIVING_SAVING_METHODS),
        )
    }

    private fun MissionSurveyAnswer.toValue(): MissionSurveyAnswerValue =
        MissionSurveyAnswerValue(
            categoryCode = categoryCode,
            questionCode = questionCode,
            valueType = valueType,
            answerCode = answerCode,
            numericValue = numericValue,
            unitCode = unitCode,
        )

    private fun unitFor(type: HobbySpendingType): SurveyFrequencyUnit =
        if (type == HobbySpendingType.SUBSCRIPTION) {
            SurveyFrequencyUnit.SUBSCRIPTION_COUNT
        } else {
            SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
        }

    private fun unitFor(area: LivingArea): SurveyFrequencyUnit =
        if (area == LivingArea.SUBSCRIPTION) {
            SurveyFrequencyUnit.SUBSCRIPTION_COUNT
        } else {
            SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
        }

    private fun internal(): Nothing = throw BaseException(ErrorCode.INTERNAL_SERVER_ERROR)

    private class CategoryRows(
        allRows: List<MissionSurveyAnswerValue>,
        category: MissionSurveyCategory,
    ) {
        private val rows = allRows.filter { it.categoryCode == category.code }

        fun isEmpty(): Boolean = rows.isEmpty()

        inline fun <reified T> single(question: MissionSurveyQuestionCode): T
            where T : Enum<T>, T : MissionSurveyCode =
            missionSurveyCodeOf(optionCodes(question).single())

        inline fun <reified T> many(question: MissionSurveyQuestionCode): List<T>
            where T : Enum<T>, T : MissionSurveyCode {
            val selected = optionCodes(question).map { missionSurveyCodeOf<T>(it) }.toSet()
            return enumValues<T>().filter(selected::contains)
        }

        fun number(
            question: MissionSurveyQuestionCode,
            subject: MissionSurveyCode,
            expectedUnit: SurveyFrequencyUnit,
        ): Int {
            val row = rows.single {
                it.questionCode == question.code &&
                    it.valueType == NUMBER_VALUE_TYPE &&
                    it.answerCode == subject.code
            }
            check(row.unitCode == expectedUnit.code)
            return checkNotNull(row.numericValue)
        }

        fun optionCodes(question: MissionSurveyQuestionCode): List<String> =
            rows.filter { it.questionCode == question.code && it.valueType == OPTION_VALUE_TYPE }
                .map(MissionSurveyAnswerValue::answerCode)
    }

    companion object {
        private const val OPTION_VALUE_TYPE = "OPTION"
        private const val NUMBER_VALUE_TYPE = "NUMBER"
        private const val SELF_ANSWER_CODE = "SELF"
    }
}
