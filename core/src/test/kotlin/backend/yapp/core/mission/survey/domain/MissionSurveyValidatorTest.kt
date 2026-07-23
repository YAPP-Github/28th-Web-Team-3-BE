package backend.yapp.core.mission.survey.domain

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissionSurveyValidatorTest {
    private val validator = MissionSurveyValidator()

    @Test
    fun `zero selected categories is rejected`() {
        assertInvalid(MissionSurveyReplaceCommand())
    }

    @Test
    fun `duplicate and exclusive meal selections are rejected`() {
        assertInvalid(
            MissionSurveyReplaceCommand(
                meal = validMeal(alternatives = listOf(MealAlternative.COOK, MealAlternative.COOK)),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                meal = validMeal(
                    alternatives = listOf(MealAlternative.COOK, MealAlternative.NO_ALTERNATIVE),
                ),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                meal = validMeal(
                    exclusions = listOf(MealExclusion.HEALTH_OR_DIET, MealExclusion.NONE),
                ),
            ),
        )
    }

    @Test
    fun `UNKNOWN meal branch requires omitted frequency and reason`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                meal = validMeal(
                    target = MealTarget.UNKNOWN,
                    weeklyFrequency = null,
                    reason = null,
                ),
            ),
        )

        assertInvalid(
            MissionSurveyReplaceCommand(
                meal = validMeal(
                    target = MealTarget.UNKNOWN,
                    weeklyFrequency = 0,
                    reason = null,
                ),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                meal = validMeal(
                    target = MealTarget.UNKNOWN,
                    weeklyFrequency = null,
                    reason = MealReason.HABIT,
                ),
            ),
        )
    }

    @Test
    fun `UNKNOWN transport branch omits frequency and normal branch requires it`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                transport = validTransport(target = TransportTarget.UNKNOWN, weeklyFrequency = null),
            ),
        )

        assertInvalid(
            MissionSurveyReplaceCommand(
                transport = validTransport(target = TransportTarget.UNKNOWN, weeklyFrequency = 0),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                transport = validTransport(target = TransportTarget.TAXI, weeklyFrequency = null),
            ),
        )
    }

    @Test
    fun `DO_NOT_REDUCE hobby branch requires its canonical empty follow-ups`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.DO_NOT_REDUCE),
                    monthlySpendingRange = null,
                    frequencies = emptyList(),
                    savingMethods = listOf(HobbySavingMethod.NO_HOBBY_MISSION),
                ),
            ),
        )

        assertInvalid(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.DO_NOT_REDUCE),
                    monthlySpendingRange = HobbySpendingRange.UNDER_50K,
                    frequencies = emptyList(),
                    savingMethods = listOf(HobbySavingMethod.NO_HOBBY_MISSION),
                ),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.DO_NOT_REDUCE),
                    monthlySpendingRange = null,
                    frequencies = emptyList(),
                    savingMethods = listOf(HobbySavingMethod.WAIT_BEFORE_BUYING),
                ),
            ),
        )
    }

    @Test
    fun `UNKNOWN living branch is exclusive and omits spending follow-ups`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.UNKNOWN),
                    monthlySpendingRange = null,
                    frequencies = emptyList(),
                ),
            ),
        )

        assertInvalid(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.UNKNOWN, LivingArea.SUBSCRIPTION),
                    monthlySpendingRange = null,
                    frequencies = emptyList(),
                ),
            ),
        )
        assertInvalid(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.UNKNOWN),
                    monthlySpendingRange = LivingSpendingRange.UNKNOWN,
                    frequencies = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `meal frequency accepts zero and exact bounds`() {
        listOf(
            validMeal(target = MealTarget.DELIVERY, weeklyFrequency = 0),
            validMeal(target = MealTarget.DELIVERY, weeklyFrequency = 7),
            validMeal(target = MealTarget.PAID_BEVERAGE, weeklyFrequency = 14),
        ).forEach { meal ->
            validator.validate(MissionSurveyReplaceCommand(meal = meal))
        }
    }

    @Test
    fun `meal and transport frequencies outside their branches are rejected`() {
        listOf(
            MissionSurveyReplaceCommand(meal = validMeal(weeklyFrequency = -1)),
            MissionSurveyReplaceCommand(meal = validMeal(weeklyFrequency = 8)),
            MissionSurveyReplaceCommand(
                meal = validMeal(target = MealTarget.PAID_BEVERAGE, weeklyFrequency = 15),
            ),
            MissionSurveyReplaceCommand(transport = validTransport(weeklyFrequency = -1)),
            MissionSurveyReplaceCommand(transport = validTransport(weeklyFrequency = 8)),
        ).forEach(::assertInvalid)
    }

    @Test
    fun `hobby keyed frequencies accept exact keys zero and both bounds`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.GOODS, HobbySpendingType.SUBSCRIPTION),
                    frequencies = listOf(
                        HobbyFrequency(HobbySpendingType.SUBSCRIPTION, 20),
                        HobbyFrequency(HobbySpendingType.GOODS, 0),
                    ),
                ),
            ),
        )
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.GOODS),
                    frequencies = listOf(HobbyFrequency(HobbySpendingType.GOODS, 31)),
                ),
            ),
        )
    }

    @Test
    fun `hobby keyed frequencies reject missing duplicate extra and out-of-range keys`() {
        listOf(
            emptyList(),
            listOf(
                HobbyFrequency(HobbySpendingType.GOODS, 1),
                HobbyFrequency(HobbySpendingType.GOODS, 2),
            ),
            listOf(HobbyFrequency(HobbySpendingType.SUBSCRIPTION, 1)),
            listOf(HobbyFrequency(HobbySpendingType.GOODS, 32)),
        ).forEach { frequencies ->
            assertInvalid(
                MissionSurveyReplaceCommand(
                    hobby = validHobby(
                        spendingTypes = listOf(HobbySpendingType.GOODS),
                        frequencies = frequencies,
                    ),
                ),
            )
        }
        assertInvalid(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.SUBSCRIPTION),
                    frequencies = listOf(HobbyFrequency(HobbySpendingType.SUBSCRIPTION, 21)),
                ),
            ),
        )
    }

    @Test
    fun `living keyed frequencies accept exact keys and reject mismatches and ranges`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.SUBSCRIPTION, LivingArea.ONLINE_SHOPPING),
                    frequencies = listOf(
                        LivingFrequency(LivingArea.ONLINE_SHOPPING, 31),
                        LivingFrequency(LivingArea.SUBSCRIPTION, 0),
                    ),
                ),
            ),
        )

        listOf(
            listOf(LivingFrequency(LivingArea.SUBSCRIPTION, 1)),
            listOf(
                LivingFrequency(LivingArea.ONLINE_SHOPPING, 1),
                LivingFrequency(LivingArea.ONLINE_SHOPPING, 2),
            ),
            listOf(LivingFrequency(LivingArea.ONLINE_SHOPPING, 32)),
        ).forEach { frequencies ->
            assertInvalid(
                MissionSurveyReplaceCommand(
                    living = validLiving(
                        areas = listOf(LivingArea.ONLINE_SHOPPING),
                        frequencies = frequencies,
                    ),
                ),
            )
        }
        assertInvalid(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.SUBSCRIPTION),
                    frequencies = listOf(LivingFrequency(LivingArea.SUBSCRIPTION, 21)),
                ),
            ),
        )
    }

    private fun assertInvalid(command: MissionSurveyReplaceCommand) {
        val exception = assertFailsWith<BaseException> { validator.validate(command) }
        assertEquals(ErrorCode.MISSION_SURVEY_INVALID, exception.errorCode)
    }

    private fun validMeal(
        target: MealTarget = MealTarget.DELIVERY,
        weeklyFrequency: Int? = 3,
        alternatives: List<MealAlternative> = listOf(MealAlternative.COOK),
        reason: MealReason? = MealReason.TIME_OR_ENERGY,
        exclusions: List<MealExclusion> = listOf(MealExclusion.NONE),
    ) = MealSurveyAnswers(target, weeklyFrequency, alternatives, reason, exclusions)

    private fun validTransport(
        target: TransportTarget = TransportTarget.TAXI,
        weeklyFrequency: Int? = 2,
    ) = TransportSurveyAnswers(
        primaryMode = TransportPrimaryMode.TAXI,
        target = target,
        weeklyFrequency = weeklyFrequency,
        reason = TransportReason.TIME_PRESSURE,
        exclusions = listOf(TransportExclusion.NONE),
    )

    private fun validHobby(
        spendingTypes: List<HobbySpendingType> = listOf(HobbySpendingType.GOODS),
        monthlySpendingRange: HobbySpendingRange? = HobbySpendingRange.UNDER_50K,
        frequencies: List<HobbyFrequency> = listOf(HobbyFrequency(HobbySpendingType.GOODS, 1)),
        savingMethods: List<HobbySavingMethod> = listOf(HobbySavingMethod.WAIT_BEFORE_BUYING),
    ) = HobbySurveyAnswers(
        hobbies = listOf(HobbyType.READING),
        spendingTypes = spendingTypes,
        monthlySpendingRange = monthlySpendingRange,
        frequencies = frequencies,
        savingMethods = savingMethods,
    )

    private fun validLiving(
        areas: List<LivingArea> = listOf(LivingArea.ONLINE_SHOPPING),
        monthlySpendingRange: LivingSpendingRange? = LivingSpendingRange.UNDER_30K,
        frequencies: List<LivingFrequency> = listOf(LivingFrequency(LivingArea.ONLINE_SHOPPING, 1)),
    ) = LivingSurveyAnswers(
        areas = areas,
        monthlySpendingRange = monthlySpendingRange,
        frequencies = frequencies,
        trigger = LivingSpendingTrigger.DISCOUNT_OR_LIMITED_SALE,
        savingMethods = listOf(LivingSavingMethod.WAIT_24_HOURS),
    )
}
