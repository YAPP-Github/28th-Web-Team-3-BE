package backend.yapp.core.mission.survey.domain

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissionSurveyValidatorTest {
    private val validator = MissionSurveyValidator(MissionSurveyQuestionCatalog())

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
                    weeklyFrequency = WeeklyFrequencyRange.ONE_TO_TWO,
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
                transport = validTransport(
                    target = TransportTarget.UNKNOWN,
                    weeklyFrequency = WeeklyFrequencyRange.ONE_TO_TWO,
                ),
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
    fun `normal hobby branch allows no hobby mission only as an exclusive choice`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    savingMethods = listOf(HobbySavingMethod.NO_HOBBY_MISSION),
                ),
            ),
        )

        assertInvalid(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    savingMethods = listOf(
                        HobbySavingMethod.WAIT_BEFORE_BUYING,
                        HobbySavingMethod.NO_HOBBY_MISSION,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `OTHER hobby requires normalized text within the configured length`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    hobbies = listOf(HobbyType.READING, HobbyType.OTHER),
                    otherHobby = "보드게임",
                ),
            ),
        )
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    hobbies = listOf(HobbyType.OTHER),
                    otherHobby = "가".repeat(HobbySurveyAnswers.MAX_OTHER_HOBBY_LENGTH),
                ),
            ),
        )

        listOf(
            validHobby(hobbies = listOf(HobbyType.OTHER), otherHobby = null),
            validHobby(hobbies = listOf(HobbyType.OTHER), otherHobby = ""),
            validHobby(hobbies = listOf(HobbyType.OTHER), otherHobby = " 보드게임 "),
            validHobby(
                hobbies = listOf(HobbyType.OTHER),
                otherHobby = "가".repeat(HobbySurveyAnswers.MAX_OTHER_HOBBY_LENGTH + 1),
            ),
            validHobby(hobbies = listOf(HobbyType.READING), otherHobby = "보드게임"),
        ).forEach { hobby ->
            assertInvalid(MissionSurveyReplaceCommand(hobby = hobby))
        }
    }

    @Test
    fun `stored legacy OTHER hobby may omit text`() {
        validator.validateStored(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    hobbies = listOf(HobbyType.OTHER),
                    otherHobby = null,
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
    fun `meal frequency accepts every configured range`() {
        listOf(
            validMeal(weeklyFrequency = WeeklyFrequencyRange.ONE_TO_TWO),
            validMeal(weeklyFrequency = WeeklyFrequencyRange.THREE_TO_FOUR),
            validMeal(weeklyFrequency = WeeklyFrequencyRange.FIVE_TO_SIX),
            validMeal(weeklyFrequency = WeeklyFrequencyRange.SEVEN_OR_MORE),
        ).forEach { meal ->
            validator.validate(MissionSurveyReplaceCommand(meal = meal))
        }
    }

    @Test
    fun `hobby keyed frequency ranges accept matching normal and subscription ranges`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.GOODS, HobbySpendingType.SUBSCRIPTION),
                    frequencies = listOf(
                        HobbyFrequency(HobbySpendingType.SUBSCRIPTION, SubscriptionCountRange.THREE_OR_MORE),
                        HobbyFrequency(HobbySpendingType.GOODS, FourWeeklyFrequencyRange.ONE_TO_TWO),
                    ),
                ),
            ),
        )
        validator.validate(
            MissionSurveyReplaceCommand(
                hobby = validHobby(
                    spendingTypes = listOf(HobbySpendingType.GOODS),
                    frequencies = listOf(HobbyFrequency(HobbySpendingType.GOODS, FourWeeklyFrequencyRange.SEVEN_OR_MORE)),
                ),
            ),
        )
    }

    @Test
    fun `hobby keyed frequency ranges reject missing duplicate extra and mismatched ranges`() {
        listOf(
            emptyList(),
            listOf(
                HobbyFrequency(HobbySpendingType.GOODS, FourWeeklyFrequencyRange.ONE_TO_TWO),
                HobbyFrequency(HobbySpendingType.GOODS, FourWeeklyFrequencyRange.THREE_TO_FOUR),
            ),
            listOf(HobbyFrequency(HobbySpendingType.SUBSCRIPTION, SubscriptionCountRange.ONE)),
            listOf(HobbyFrequency(HobbySpendingType.GOODS, SubscriptionCountRange.ONE)),
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
                    frequencies = listOf(HobbyFrequency(HobbySpendingType.SUBSCRIPTION, FourWeeklyFrequencyRange.ONE_TO_TWO)),
                ),
            ),
        )
    }

    @Test
    fun `living keyed frequency ranges accept matching normal and subscription ranges`() {
        validator.validate(
            MissionSurveyReplaceCommand(
                living = validLiving(
                    areas = listOf(LivingArea.SUBSCRIPTION, LivingArea.ONLINE_SHOPPING),
                    frequencies = listOf(
                        LivingFrequency(LivingArea.ONLINE_SHOPPING, FourWeeklyFrequencyRange.SEVEN_OR_MORE),
                        LivingFrequency(LivingArea.SUBSCRIPTION, SubscriptionCountRange.ONE),
                    ),
                ),
            ),
        )

        listOf(
            listOf(LivingFrequency(LivingArea.SUBSCRIPTION, SubscriptionCountRange.ONE)),
            listOf(
                LivingFrequency(LivingArea.ONLINE_SHOPPING, FourWeeklyFrequencyRange.ONE_TO_TWO),
                LivingFrequency(LivingArea.ONLINE_SHOPPING, FourWeeklyFrequencyRange.THREE_TO_FOUR),
            ),
            listOf(LivingFrequency(LivingArea.ONLINE_SHOPPING, SubscriptionCountRange.ONE)),
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
                    frequencies = listOf(LivingFrequency(LivingArea.SUBSCRIPTION, FourWeeklyFrequencyRange.ONE_TO_TWO)),
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
        weeklyFrequency: WeeklyFrequencyRange? = WeeklyFrequencyRange.THREE_TO_FOUR,
        alternatives: List<MealAlternative> = listOf(MealAlternative.COOK),
        reason: MealReason? = MealReason.TIME_OR_ENERGY,
        exclusions: List<MealExclusion> = listOf(MealExclusion.NONE),
    ) = MealSurveyAnswers(target, weeklyFrequency, alternatives, reason, exclusions)

    private fun validTransport(
        target: TransportTarget = TransportTarget.TAXI,
        weeklyFrequency: WeeklyFrequencyRange? = WeeklyFrequencyRange.ONE_TO_TWO,
    ) = TransportSurveyAnswers(
        primaryMode = TransportPrimaryMode.TAXI,
        target = target,
        weeklyFrequency = weeklyFrequency,
        reason = TransportReason.TIME_PRESSURE,
        exclusions = listOf(TransportExclusion.NONE),
    )

    private fun validHobby(
        hobbies: List<HobbyType> = listOf(HobbyType.READING),
        otherHobby: String? = null,
        spendingTypes: List<HobbySpendingType> = listOf(HobbySpendingType.GOODS),
        monthlySpendingRange: HobbySpendingRange? = HobbySpendingRange.UNDER_50K,
        frequencies: List<HobbyFrequency> = listOf(HobbyFrequency(HobbySpendingType.GOODS, FourWeeklyFrequencyRange.ONE_TO_TWO)),
        savingMethods: List<HobbySavingMethod> = listOf(HobbySavingMethod.WAIT_BEFORE_BUYING),
    ) = HobbySurveyAnswers(
        hobbies = hobbies,
        spendingTypes = spendingTypes,
        monthlySpendingRange = monthlySpendingRange,
        frequencies = frequencies,
        savingMethods = savingMethods,
        otherHobby = otherHobby,
    )

    private fun validLiving(
        areas: List<LivingArea> = listOf(LivingArea.ONLINE_SHOPPING),
        monthlySpendingRange: LivingSpendingRange? = LivingSpendingRange.UNDER_30K,
        frequencies: List<LivingFrequency> = listOf(LivingFrequency(LivingArea.ONLINE_SHOPPING, FourWeeklyFrequencyRange.ONE_TO_TWO)),
    ) = LivingSurveyAnswers(
        areas = areas,
        monthlySpendingRange = monthlySpendingRange,
        frequencies = frequencies,
        trigger = LivingSpendingTrigger.DISCOUNT_OR_LIMITED_SALE,
        savingMethods = listOf(LivingSavingMethod.WAIT_24_HOURS),
    )
}
