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
import backend.yapp.core.mission.survey.domain.SurveyFrequencyUnit
import backend.yapp.core.mission.survey.domain.WeeklyFrequencyRange
import backend.yapp.core.mission.survey.domain.missionSurveyCodeOf
import backend.yapp.core.mission.survey.domain.surveyFrequencyRangeOf
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

data class MissionSurveyPutRequest(
    @field:Schema(description = "식사 카테고리 응답. 선택하지 않은 카테고리면 생략하거나 null로 보낸다.", nullable = true)
    val meal: MealSurveyRequest? = null,
    @field:Schema(description = "교통 카테고리 응답. 선택하지 않은 카테고리면 생략하거나 null로 보낸다.", nullable = true)
    val transport: TransportSurveyRequest? = null,
    @field:Schema(description = "취미 카테고리 응답. 선택하지 않은 카테고리면 생략하거나 null로 보낸다.", nullable = true)
    val hobby: HobbySurveyRequest? = null,
    @field:Schema(description = "생활 카테고리 응답. 선택하지 않은 카테고리면 생략하거나 null로 보낸다.", nullable = true)
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
    @field:Schema(
        description = "줄이고 싶은 식사 소비 유형. UNKNOWN은 다른 값과 함께 쓰지 않는 단일 선택 값이다.",
        example = "DELIVERY",
        allowableValues = [
            "DELIVERY",
            "DINING_OUT",
            "PAID_BEVERAGE",
            "CONVENIENCE_FOOD",
            "DRINKING_GATHERING",
            "UNKNOWN",
        ],
    )
    val target: String,
    @field:Schema(
        description = "target의 주간 이용 빈도 범위. target이 UNKNOWN이면 생략하거나 null.",
        example = "THREE_TO_FOUR",
        allowableValues = ["ONE_TO_TWO", "THREE_TO_FOUR", "FIVE_TO_SIX", "SEVEN_OR_MORE"],
        nullable = true,
    )
    val weeklyFrequencyRange: String? = null,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "실천 가능한 식사 대안. 1~7개 선택하며 NO_ALTERNATIVE는 다른 값과 함께 보낼 수 없다.",
        ),
        schema = Schema(
            allowableValues = [
                "COOK",
                "PREPARE_MEAL",
                "PREPARE_BEVERAGE",
                "PICKUP",
                "USE_FRIDGE_FIRST",
                "BUY_PLANNED_INGREDIENTS",
                "NO_ALTERNATIVE",
            ],
        ),
        minItems = 1,
        maxItems = 7,
        uniqueItems = true,
    )
    val alternatives: List<String>,
    @field:Schema(
        description = "해당 소비의 주된 이유. target이 UNKNOWN이면 생략하거나 null.",
        example = "TIME_OR_ENERGY",
        allowableValues = [
            "TIME_OR_ENERGY",
            "HABIT",
            "SOCIAL",
            "DISCOUNT_OR_NOTIFICATION",
            "NO_COOKING_OR_STORAGE",
            "ALTERNATIVE_INCONVENIENT",
        ],
        nullable = true,
    )
    val reason: String? = null,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "식사 미션 제외 조건. 1~6개 선택하며 NONE은 다른 값과 함께 보낼 수 없다.",
        ),
        schema = Schema(
            allowableValues = [
                "HEALTH_OR_DIET",
                "FIXED_MEAL",
                "NO_COOKING_ENVIRONMENT",
                "UNAVOIDABLE_SCHEDULE",
                "NO_REDUCE_FOOD_AMOUNT",
                "NONE",
            ],
        ),
        minItems = 1,
        maxItems = 6,
        uniqueItems = true,
    )
    val exclusions: List<String>,
) {
    fun toAnswers(): MealSurveyAnswers =
        MealSurveyAnswers(
            target = parseCode(target),
            weeklyFrequency = weeklyFrequencyRange?.let(::parseCode),
            alternatives = alternatives.map(::parseCode),
            reason = reason?.let(::parseCode),
            exclusions = exclusions.map(::parseCode),
        )
}

data class TransportSurveyRequest(
    @field:Schema(
        description = "평소 주로 이용하는 이동수단.",
        example = "TAXI",
        allowableValues = [
            "PUBLIC_TRANSIT",
            "TAXI",
            "CAR",
            "WALK_OR_BICYCLE",
            "SHARED_MOBILITY",
            "VARIES",
        ],
    )
    val primaryMode: String,
    @field:Schema(
        description = "줄이고 싶은 교통 소비 유형. UNKNOWN은 다른 값과 함께 쓰지 않는 단일 선택 값이다.",
        example = "TAXI",
        allowableValues = [
            "TAXI",
            "SHORT_DISTANCE_PAID_MOVE",
            "CAR_DRIVING",
            "PARKING_OR_TOLL",
            "RUSH_COST",
            "UNKNOWN",
        ],
    )
    val target: String,
    @field:Schema(
        description = "target의 주간 이용 빈도 범위. target이 UNKNOWN이면 생략하거나 null.",
        example = "ONE_TO_TWO",
        allowableValues = ["ONE_TO_TWO", "THREE_TO_FOUR", "FIVE_TO_SIX", "SEVEN_OR_MORE"],
        nullable = true,
    )
    val weeklyFrequencyRange: String? = null,
    @field:Schema(
        description = "해당 이동수단을 이용하는 주된 이유. target이 UNKNOWN이어도 필수다.",
        example = "TIME_PRESSURE",
        allowableValues = [
            "LATE_NIGHT_OR_SAFETY",
            "TIME_PRESSURE",
            "WEATHER",
            "LUGGAGE_OR_CARE",
            "POOR_TRANSIT_CONNECTION",
            "WALKING_DIFFICULTY",
            "CONVENIENCE_OR_HABIT",
        ],
    )
    val reason: String,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "교통 미션 제외 조건. 1~6개 선택하며 NONE은 다른 값과 함께 보낼 수 없다.",
        ),
        schema = Schema(
            allowableValues = [
                "LATE_NIGHT_DANGER",
                "EXTREME_WEATHER",
                "MOBILITY_CONSTRAINT",
                "LUGGAGE_OR_CARE",
                "NO_TRANSIT",
                "NONE",
            ],
        ),
        minItems = 1,
        maxItems = 6,
        uniqueItems = true,
    )
    val exclusions: List<String>,
) {
    fun toAnswers(): TransportSurveyAnswers =
        TransportSurveyAnswers(
            primaryMode = parseCode<TransportPrimaryMode>(primaryMode),
            target = parseCode<TransportTarget>(target),
            weeklyFrequency = weeklyFrequencyRange?.let(::parseCode),
            reason = parseCode<TransportReason>(reason),
            exclusions = exclusions.map(::parseCode),
        )
}

data class HobbySurveyRequest(
    @field:ArraySchema(
        arraySchema = Schema(description = "평소 즐기는 취미. 중복 없이 1~9개 선택한다."),
        schema = Schema(
            allowableValues = [
                "READING",
                "MOVIE_OR_OTT",
                "GAME",
                "EXERCISE",
                "PERFORMANCE_OR_EXHIBITION",
                "MUSIC_OR_CREATION",
                "TRAVEL_OR_OUTDOOR",
                "GATHERING",
                "OTHER",
            ],
        ),
        minItems = 1,
        maxItems = 9,
        uniqueItems = true,
    )
    val hobbies: List<String>,
    @field:Schema(
        description = "hobbies에 OTHER가 포함되면 필수인 기타 취미명. 앞뒤 공백을 제거한 1~50자 문자열이며, OTHER가 없으면 생략하거나 null로 보낸다.",
        example = "보드게임",
        maxLength = 50,
        nullable = true,
    )
    val otherHobby: String? = null,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "줄이고 싶은 취미 지출 유형. 중복 없이 1~2개 선택하며 DO_NOT_REDUCE는 단독으로만 보낸다.",
        ),
        schema = Schema(
            allowableValues = [
                "GOODS",
                "DIGITAL_CONTENT",
                "SUBSCRIPTION",
                "CLASS",
                "TICKET",
                "GATHERING_FEE",
                "RENTAL_OR_SPACE",
                "DO_NOT_REDUCE",
            ],
        ),
        minItems = 1,
        maxItems = 2,
        uniqueItems = true,
    )
    val spendingTypes: List<String>,
    @field:Schema(
        description = "최근 3개월 월평균 취미비. spendingTypes가 [DO_NOT_REDUCE]이면 생략하거나 null.",
        example = "FROM_50K_TO_150K",
        allowableValues = [
            "UNDER_50K",
            "FROM_50K_TO_150K",
            "FROM_150K_TO_300K",
            "OVER_300K",
            "UNKNOWN",
        ],
        nullable = true,
    )
    val monthlySpendingRange: String? = null,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "spendingTypes별 결제 빈도. 일반 분기에서는 key 집합과 항목 수가 spendingTypes와 정확히 일치해야 하며, DO_NOT_REDUCE 분기에서는 빈 배열이다.",
        ),
        minItems = 0,
        maxItems = 2,
    )
    val frequencies: List<HobbyFrequencyRequest>,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "허용 가능한 취미 절약 방식. 중복 없이 1~7개 선택한다. NO_HOBBY_MISSION은 단독 선택이며, spendingTypes가 [DO_NOT_REDUCE]이면 [NO_HOBBY_MISSION]만 보낸다.",
        ),
        schema = Schema(
            allowableValues = [
                "WAIT_BEFORE_BUYING",
                "SET_WEEKLY_LIMIT",
                "USE_OWNED_FIRST",
                "USE_CHEAPER_ALTERNATIVE",
                "REVIEW_SUBSCRIPTIONS",
                "KEEP_TIME_REDUCE_COST",
                "NO_HOBBY_MISSION",
            ],
        ),
        minItems = 1,
        maxItems = 7,
        uniqueItems = true,
    )
    val savingMethods: List<String>,
) {
    fun toAnswers(): HobbySurveyAnswers =
        HobbySurveyAnswers(
            hobbies = hobbies.map(::parseCode),
            otherHobby = otherHobby?.trim(),
            spendingTypes = spendingTypes.map(::parseCode),
            monthlySpendingRange = monthlySpendingRange?.let(::parseCode),
            frequencies = frequencies.map(HobbyFrequencyRequest::toDomain),
            savingMethods = savingMethods.map(::parseCode),
        )
}

data class HobbyFrequencyRequest(
    @field:Schema(
        description = "앞서 spendingTypes에서 선택한 지출 유형. 각 선택값이 정확히 한 번씩 등장해야 하며 DO_NOT_REDUCE는 허용되지 않는다.",
        example = "SUBSCRIPTION",
        allowableValues = [
            "GOODS",
            "DIGITAL_CONTENT",
            "SUBSCRIPTION",
            "CLASS",
            "TICKET",
            "GATHERING_FEE",
            "RENTAL_OR_SPACE",
        ],
    )
    val spendingType: String,
    @field:Schema(
        description = "해당 지출의 빈도 범위. SUBSCRIPTION은 1개·2개·3개 이상, 그 외는 4주 기준 1~2회·3~4회·5~6회·7회 이상.",
        example = "THREE_TO_FOUR",
    )
    val frequencyRange: String,
) {
    fun toDomain(): HobbyFrequency {
        val type = parseCode<HobbySpendingType>(spendingType)
        val unit = if (type == HobbySpendingType.SUBSCRIPTION) SurveyFrequencyUnit.SUBSCRIPTION_COUNT else SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
        return HobbyFrequency(type, surveyFrequencyRangeOf(frequencyRange, unit))
    }
}

data class LivingSurveyRequest(
    @field:ArraySchema(
        arraySchema = Schema(
            description = "줄이고 싶은 생활비 영역. 중복 없이 1~2개 선택하며 UNKNOWN은 단독으로만 보낸다.",
        ),
        schema = Schema(
            allowableValues = [
                "SUBSCRIPTION",
                "ONLINE_SHOPPING",
                "CLOTHING",
                "HOUSEHOLD_GOODS",
                "CONVENIENCE_PURCHASE",
                "BEAUTY",
                "EXERCISE_OR_LEARNING",
                "UNKNOWN",
            ],
        ),
        minItems = 1,
        maxItems = 2,
        uniqueItems = true,
    )
    val areas: List<String>,
    @field:Schema(
        description = "선택 영역의 월평균 지출. areas가 [UNKNOWN]이면 생략하거나 null.",
        example = "FROM_30K_TO_100K",
        allowableValues = [
            "UNDER_30K",
            "FROM_30K_TO_100K",
            "FROM_100K_TO_300K",
            "OVER_300K",
            "UNKNOWN",
        ],
        nullable = true,
    )
    val monthlySpendingRange: String? = null,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "areas별 소비 빈도. 일반 분기에서는 key 집합과 항목 수가 areas와 정확히 일치해야 하며, UNKNOWN 분기에서는 빈 배열이다.",
        ),
        minItems = 0,
        maxItems = 2,
    )
    val frequencies: List<LivingFrequencyRequest>,
    @field:Schema(
        description = "예정에 없던 생활비 소비가 발생하는 주된 계기. areas가 UNKNOWN이어도 필수다.",
        example = "DISCOUNT_OR_LIMITED_SALE",
        allowableValues = [
            "DISCOUNT_OR_LIMITED_SALE",
            "AD_OR_SOCIAL_MEDIA",
            "INVENTORY_UNCHECKED",
            "STRESS_OR_BOREDOM",
            "CONVENIENCE",
            "FORGOT_AUTO_PAYMENT",
            "RARELY_UNPLANNED",
        ],
    )
    val trigger: String,
    @field:ArraySchema(
        arraySchema = Schema(
            description = "허용 가능한 생활비 절약 방식. 중복 없이 1~8개 선택하며 NO_LIVING_MISSION은 단독으로만 보낸다.",
        ),
        schema = Schema(
            allowableValues = [
                "WAIT_24_HOURS",
                "USE_SHOPPING_LIST",
                "CHECK_INVENTORY",
                "LIMIT_FREQUENCY",
                "REVIEW_SUBSCRIPTIONS",
                "CONSIDER_REUSE",
                "EXCLUDE_NECESSARY_COST",
                "NO_LIVING_MISSION",
            ],
        ),
        minItems = 1,
        maxItems = 8,
        uniqueItems = true,
    )
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
    @field:Schema(
        description = "앞서 areas에서 선택한 생활비 영역. 각 선택값이 정확히 한 번씩 등장해야 하며 UNKNOWN은 허용되지 않는다.",
        example = "SUBSCRIPTION",
        allowableValues = [
            "SUBSCRIPTION",
            "ONLINE_SHOPPING",
            "CLOTHING",
            "HOUSEHOLD_GOODS",
            "CONVENIENCE_PURCHASE",
            "BEAUTY",
            "EXERCISE_OR_LEARNING",
        ],
    )
    val area: String,
    @field:Schema(
        description = "해당 영역의 빈도 범위. SUBSCRIPTION은 1개·2개·3개 이상, 그 외는 4주 기준 1~2회·3~4회·5~6회·7회 이상.",
        example = "THREE_TO_FOUR",
    )
    val frequencyRange: String,
) {
    fun toDomain(): LivingFrequency {
        val livingArea = parseCode<LivingArea>(area)
        val unit = if (livingArea == LivingArea.SUBSCRIPTION) SurveyFrequencyUnit.SUBSCRIPTION_COUNT else SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
        return LivingFrequency(livingArea, surveyFrequencyRangeOf(frequencyRange, unit))
    }
}

private inline fun <reified T> parseCode(value: String): T
    where T : Enum<T>, T : MissionSurveyCode =
    try {
        missionSurveyCodeOf(value)
    } catch (_: IllegalArgumentException) {
        throw BaseException(ErrorCode.MISSION_SURVEY_INVALID)
    }
