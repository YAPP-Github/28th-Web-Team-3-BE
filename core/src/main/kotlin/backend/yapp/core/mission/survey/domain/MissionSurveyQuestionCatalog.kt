package backend.yapp.core.mission.survey.domain

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import org.springframework.stereotype.Component

data class MissionSurveyQuestionDefinition(
    val code: String,
    val prompt: String,
    val answerType: SurveyAnswerType,
    val options: List<MissionSurveyOptionDefinition> = emptyList(),
    val minSelections: Int? = null,
    val maxSelections: Int? = null,
    val dependsOnQuestionCode: String? = null,
    val skipWhenOptionCodes: List<String> = emptyList(),
    val numericRules: List<MissionSurveyNumericRule> = emptyList(),
    val exclusiveOptionCodes: List<String> = emptyList(),
    val conditionalOptionRules: List<MissionSurveyConditionalOptionRule> = emptyList(),
    val impacts: List<MissionSurveyImpact>,
)

data class MissionSurveyOptionDefinition(
    val code: String,
    val label: String,
)

data class MissionSurveyNumericRule(
    val subjectOptionCode: String,
    val unit: SurveyFrequencyUnit,
    val minimum: Int,
    val maximum: Int,
)

data class MissionSurveyConditionalOptionRule(
    val dependsOnQuestionCode: String,
    val whenOptionCodes: List<String>,
    val allowedOptionCodes: List<String>,
)

data class MissionSurveyCategoryQuestions(
    val category: MissionSurveyCategory,
    val questions: List<MissionSurveyQuestionDefinition>,
)

@Component
class MissionSurveyQuestionCatalog {
    fun categories(rawCategories: List<String>): List<MissionSurveyCategory> {
        val codes = rawCategories.flatMap { entry -> entry.split(",") }
            .map(String::trim)
        if (codes.any(String::isEmpty)) invalid()
        if (codes.size !in 1..4 || codes.distinct().size != codes.size) invalid()

        val categories = codes.map { code ->
            MissionSurveyCategory.entries.firstOrNull { it.code == code } ?: invalid()
        }
        return MissionSurveyCategory.entries.filter(categories::contains)
    }

    fun questions(categories: List<MissionSurveyCategory>): List<MissionSurveyCategoryQuestions> =
        MissionSurveyCategory.entries
            .filter(categories::contains)
            .map { category -> MissionSurveyCategoryQuestions(category, definitions.getValue(category)) }

    private fun invalid(): Nothing = throw BaseException(ErrorCode.MISSION_SURVEY_INVALID)

    private val definitions: Map<MissionSurveyCategory, List<MissionSurveyQuestionDefinition>> = mapOf(
        MissionSurveyCategory.MEAL to mealQuestions(),
        MissionSurveyCategory.TRANSPORT to transportQuestions(),
        MissionSurveyCategory.HOBBY to hobbyQuestions(),
        MissionSurveyCategory.LIVING to livingQuestions(),
    )

    private fun mealQuestions(): List<MissionSurveyQuestionDefinition> = listOf(
        choice(
            MissionSurveyQuestionCode.MEAL_TARGET,
            "식사비 중 가장 먼저 바꾸고 싶은 습관은 무엇인가요?",
            options(
                MealTarget.DELIVERY to "배달 음식",
                MealTarget.DINING_OUT to "외식",
                MealTarget.PAID_BEVERAGE to "카페·유료 음료",
                MealTarget.CONVENIENCE_FOOD to "편의점 식사·간식",
                MealTarget.DRINKING_GATHERING to "술자리·회식",
                MealTarget.UNKNOWN to "아직 잘 모르겠어요",
            ),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER, MissionSurveyImpact.REDUCTION_TARGET),
        ),
        number(
            MissionSurveyQuestionCode.MEAL_FREQUENCY,
            "선택한 항목을 평소 한 주에 몇 번 이용하나요?",
            MissionSurveyQuestionCode.MEAL_TARGET,
            skipWhen = listOf(MealTarget.UNKNOWN),
            rules = MealTarget.entries.filterNot { it == MealTarget.UNKNOWN }.map {
                MissionSurveyNumericRule(
                    it.code,
                    SurveyFrequencyUnit.TIMES_PER_WEEK,
                    0,
                    if (it == MealTarget.PAID_BEVERAGE) 14 else 7,
                )
            },
            impacts = listOf(MissionSurveyImpact.BASELINE_FREQUENCY),
        ),
        multiChoice(
            MissionSurveyQuestionCode.MEAL_ALTERNATIVES,
            "식사비를 줄일 때 사용할 수 있는 대안은 무엇인가요?",
            options(
                MealAlternative.COOK to "직접 요리",
                MealAlternative.PREPARE_MEAL to "도시락·간편식 준비",
                MealAlternative.PREPARE_BEVERAGE to "집이나 직장에서 음료 준비",
                MealAlternative.PICKUP to "배달 대신 포장",
                MealAlternative.USE_FRIDGE_FIRST to "냉장고 음식 먼저 사용",
                MealAlternative.BUY_PLANNED_INGREDIENTS to "계획한 식재료만 구매",
                MealAlternative.NO_ALTERNATIVE to "가능한 대안이 없음",
            ),
            maximum = MealAlternative.entries.size,
            exclusive = listOf(MealAlternative.NO_ALTERNATIVE),
            impacts = listOf(MissionSurveyImpact.ALTERNATIVE_ACTION),
        ),
        choice(
            MissionSurveyQuestionCode.MEAL_REASON,
            "해당 소비가 발생하는 가장 큰 이유는 무엇인가요?",
            options(
                MealReason.TIME_OR_ENERGY to "시간·체력 부족",
                MealReason.HABIT to "습관",
                MealReason.SOCIAL to "약속·사교 활동",
                MealReason.DISCOUNT_OR_NOTIFICATION to "할인·쿠폰·알림",
                MealReason.NO_COOKING_OR_STORAGE to "조리·보관 환경 부족",
                MealReason.ALTERNATIVE_INCONVENIENT to "다른 대안이 불편함",
            ),
            dependsOn = MissionSurveyQuestionCode.MEAL_TARGET,
            skipWhen = listOf(MealTarget.UNKNOWN),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        multiChoice(
            MissionSurveyQuestionCode.MEAL_EXCLUSIONS,
            "식사 미션에서 제외해야 할 상황이 있나요?",
            options(
                MealExclusion.HEALTH_OR_DIET to "건강·식단상 필요한 식사",
                MealExclusion.FIXED_MEAL to "회사·학교에서 정해진 식사",
                MealExclusion.NO_COOKING_ENVIRONMENT to "조리하기 어려운 환경",
                MealExclusion.UNAVOIDABLE_SCHEDULE to "피하기 어려운 회식·가족 일정",
                MealExclusion.NO_REDUCE_FOOD_AMOUNT to "식사량 자체를 줄이는 미션",
                MealExclusion.NONE to "없음",
            ),
            maximum = MealExclusion.entries.size,
            exclusive = listOf(MealExclusion.NONE),
            impacts = listOf(MissionSurveyImpact.EXCLUSION_CONDITION),
        ),
    )

    private fun transportQuestions(): List<MissionSurveyQuestionDefinition> = listOf(
        choice(
            MissionSurveyQuestionCode.TRANSPORT_PRIMARY_MODE,
            "평소 이용하는 주된 이동수단은 무엇인가요?",
            options(
                TransportPrimaryMode.PUBLIC_TRANSIT to "버스·지하철",
                TransportPrimaryMode.TAXI to "택시",
                TransportPrimaryMode.CAR to "자가용",
                TransportPrimaryMode.WALK_OR_BICYCLE to "도보·자전거",
                TransportPrimaryMode.SHARED_MOBILITY to "공유 이동수단",
                TransportPrimaryMode.VARIES to "상황에 따라 다름",
            ),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        choice(
            MissionSurveyQuestionCode.TRANSPORT_TARGET,
            "교통비 중 가장 바꾸고 싶은 습관은 무엇인가요?",
            options(
                TransportTarget.TAXI to "택시 이용",
                TransportTarget.SHORT_DISTANCE_PAID_MOVE to "가까운 거리의 차량·대중교통 이용",
                TransportTarget.CAR_DRIVING to "자가용 운행",
                TransportTarget.PARKING_OR_TOLL to "주차비·통행료",
                TransportTarget.RUSH_COST to "급하게 이동하면서 생기는 비용",
                TransportTarget.UNKNOWN to "아직 잘 모르겠어요",
            ),
            impacts = listOf(MissionSurveyImpact.REDUCTION_TARGET),
        ),
        number(
            MissionSurveyQuestionCode.TRANSPORT_FREQUENCY,
            "선택한 이동을 평소 한 주에 몇 번 이용하나요?",
            MissionSurveyQuestionCode.TRANSPORT_TARGET,
            skipWhen = listOf(TransportTarget.UNKNOWN),
            rules = TransportTarget.entries.filterNot { it == TransportTarget.UNKNOWN }.map {
                MissionSurveyNumericRule(it.code, SurveyFrequencyUnit.TIMES_PER_WEEK, 0, 7)
            },
            impacts = listOf(MissionSurveyImpact.BASELINE_FREQUENCY),
        ),
        choice(
            MissionSurveyQuestionCode.TRANSPORT_REASON,
            "해당 이동수단을 이용하는 이유는 무엇인가요?",
            options(
                TransportReason.LATE_NIGHT_OR_SAFETY to "심야·안전",
                TransportReason.TIME_PRESSURE to "시간 부족·지각 우려",
                TransportReason.WEATHER to "날씨",
                TransportReason.LUGGAGE_OR_CARE to "짐·동행자·돌봄",
                TransportReason.POOR_TRANSIT_CONNECTION to "대중교통 연결이 불편함",
                TransportReason.WALKING_DIFFICULTY to "도보·자전거 이용이 어려움",
                TransportReason.CONVENIENCE_OR_HABIT to "편리해서 또는 습관적으로",
            ),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        multiChoice(
            MissionSurveyQuestionCode.TRANSPORT_EXCLUSIONS,
            "다른 이동 방식으로 바꾸면 안 되는 상황이 있나요?",
            options(
                TransportExclusion.LATE_NIGHT_DANGER to "심야·치안상 위험",
                TransportExclusion.EXTREME_WEATHER to "폭우·폭염·폭설",
                TransportExclusion.MOBILITY_CONSTRAINT to "건강·이동상 제약",
                TransportExclusion.LUGGAGE_OR_CARE to "짐·아동·가족 돌봄",
                TransportExclusion.NO_TRANSIT to "대중교통 미운행 시간·지역",
                TransportExclusion.NONE to "없음",
            ),
            maximum = TransportExclusion.entries.size,
            exclusive = listOf(TransportExclusion.NONE),
            impacts = listOf(MissionSurveyImpact.EXCLUSION_CONDITION),
        ),
    )

    private fun hobbyQuestions(): List<MissionSurveyQuestionDefinition> = listOf(
        multiChoice(
            MissionSurveyQuestionCode.HOBBY_TYPES,
            "평소 즐기는 취미는 무엇인가요?",
            options(
                HobbyType.READING to "독서",
                HobbyType.MOVIE_OR_OTT to "영화·OTT",
                HobbyType.GAME to "게임",
                HobbyType.EXERCISE to "운동",
                HobbyType.PERFORMANCE_OR_EXHIBITION to "공연·전시",
                HobbyType.MUSIC_OR_CREATION to "음악·창작",
                HobbyType.TRAVEL_OR_OUTDOOR to "여행·야외 활동",
                HobbyType.GATHERING to "모임",
                HobbyType.OTHER to "기타",
            ),
            maximum = HobbyType.entries.size,
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        multiChoice(
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES,
            "취미비는 주로 어디에 사용하나요?",
            options(
                HobbySpendingType.GOODS to "용품·굿즈",
                HobbySpendingType.DIGITAL_CONTENT to "게임·디지털 콘텐츠",
                HobbySpendingType.SUBSCRIPTION to "정기구독",
                HobbySpendingType.CLASS to "수업·클래스",
                HobbySpendingType.TICKET to "공연·전시·티켓",
                HobbySpendingType.GATHERING_FEE to "모임비",
                HobbySpendingType.RENTAL_OR_SPACE to "장비 대여·공간 이용",
                HobbySpendingType.DO_NOT_REDUCE to "취미비는 줄이고 싶지 않음",
            ),
            maximum = 2,
            exclusive = listOf(HobbySpendingType.DO_NOT_REDUCE),
            impacts = listOf(MissionSurveyImpact.REDUCTION_TARGET),
        ),
        choice(
            MissionSurveyQuestionCode.HOBBY_MONTHLY_SPENDING,
            "최근 3개월 월평균 취미비는 어느 정도인가요?",
            options(
                HobbySpendingRange.UNDER_50K to "5만 원 미만",
                HobbySpendingRange.FROM_50K_TO_150K to "5만~15만 원",
                HobbySpendingRange.FROM_150K_TO_300K to "15만~30만 원",
                HobbySpendingRange.OVER_300K to "30만 원 이상",
                HobbySpendingRange.UNKNOWN to "잘 모르겠어요",
            ),
            dependsOn = MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES,
            skipWhen = listOf(HobbySpendingType.DO_NOT_REDUCE),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        keyedNumber(
            MissionSurveyQuestionCode.HOBBY_FREQUENCIES,
            "선택한 항목에 평소 얼마나 자주 결제하나요?",
            MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES,
            skipWhen = listOf(HobbySpendingType.DO_NOT_REDUCE),
            rules = HobbySpendingType.entries.filterNot { it == HobbySpendingType.DO_NOT_REDUCE }.map {
                MissionSurveyNumericRule(
                    it.code,
                    if (it == HobbySpendingType.SUBSCRIPTION) {
                        SurveyFrequencyUnit.SUBSCRIPTION_COUNT
                    } else {
                        SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
                    },
                    0,
                    if (it == HobbySpendingType.SUBSCRIPTION) 20 else 31,
                )
            },
            impacts = listOf(MissionSurveyImpact.BASELINE_FREQUENCY),
        ),
        multiChoice(
            MissionSurveyQuestionCode.HOBBY_SAVING_METHODS,
            "어떤 취미 절약 방식이라면 괜찮나요?",
            options(
                HobbySavingMethod.WAIT_BEFORE_BUYING to "구매 전 하루 이상 기다리기",
                HobbySavingMethod.SET_WEEKLY_LIMIT to "주간 구매 횟수 정하기",
                HobbySavingMethod.USE_OWNED_FIRST to "가진 용품·콘텐츠 먼저 사용",
                HobbySavingMethod.USE_CHEAPER_ALTERNATIVE to "무료·저렴한 대안 사용",
                HobbySavingMethod.REVIEW_SUBSCRIPTIONS to "이용하지 않는 구독 점검",
                HobbySavingMethod.KEEP_TIME_REDUCE_COST to "취미 시간은 유지하고 비용만 줄이기",
                HobbySavingMethod.NO_HOBBY_MISSION to "취미 관련 미션은 원하지 않음",
            ),
            maximum = HobbySavingMethod.entries.size,
            exclusive = listOf(HobbySavingMethod.NO_HOBBY_MISSION),
            conditionalOptionRules = listOf(
                MissionSurveyConditionalOptionRule(
                    dependsOnQuestionCode = MissionSurveyQuestionCode.HOBBY_SPENDING_TYPES.code,
                    whenOptionCodes = listOf(HobbySpendingType.DO_NOT_REDUCE.code),
                    allowedOptionCodes = listOf(HobbySavingMethod.NO_HOBBY_MISSION.code),
                ),
            ),
            impacts = listOf(MissionSurveyImpact.ALTERNATIVE_ACTION, MissionSurveyImpact.EXCLUSION_CONDITION),
        ),
    )

    private fun livingQuestions(): List<MissionSurveyQuestionDefinition> = listOf(
        multiChoice(
            MissionSurveyQuestionCode.LIVING_AREAS,
            "생활비 중 가장 먼저 바꾸고 싶은 영역은 무엇인가요?",
            options(
                LivingArea.SUBSCRIPTION to "정기구독",
                LivingArea.ONLINE_SHOPPING to "온라인 쇼핑",
                LivingArea.CLOTHING to "의류·잡화",
                LivingArea.HOUSEHOLD_GOODS to "생활용품",
                LivingArea.CONVENIENCE_PURCHASE to "편의점·소액 구매",
                LivingArea.BEAUTY to "미용·뷰티",
                LivingArea.EXERCISE_OR_LEARNING to "운동·자기계발",
                LivingArea.UNKNOWN to "아직 잘 모르겠어요",
            ),
            maximum = 2,
            exclusive = listOf(LivingArea.UNKNOWN),
            impacts = listOf(MissionSurveyImpact.REDUCTION_TARGET),
        ),
        choice(
            MissionSurveyQuestionCode.LIVING_MONTHLY_SPENDING,
            "선택한 영역에 월평균 얼마 정도 사용하나요?",
            options(
                LivingSpendingRange.UNDER_30K to "3만 원 미만",
                LivingSpendingRange.FROM_30K_TO_100K to "3만~10만 원",
                LivingSpendingRange.FROM_100K_TO_300K to "10만~30만 원",
                LivingSpendingRange.OVER_300K to "30만 원 이상",
                LivingSpendingRange.UNKNOWN to "잘 모르겠어요",
            ),
            dependsOn = MissionSurveyQuestionCode.LIVING_AREAS,
            skipWhen = listOf(LivingArea.UNKNOWN),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        keyedNumber(
            MissionSurveyQuestionCode.LIVING_FREQUENCIES,
            "해당 소비는 얼마나 자주 발생하나요?",
            MissionSurveyQuestionCode.LIVING_AREAS,
            skipWhen = listOf(LivingArea.UNKNOWN),
            rules = LivingArea.entries.filterNot { it == LivingArea.UNKNOWN }.map {
                MissionSurveyNumericRule(
                    it.code,
                    if (it == LivingArea.SUBSCRIPTION) {
                        SurveyFrequencyUnit.SUBSCRIPTION_COUNT
                    } else {
                        SurveyFrequencyUnit.TIMES_PER_FOUR_WEEKS
                    },
                    0,
                    if (it == LivingArea.SUBSCRIPTION) 20 else 31,
                )
            },
            impacts = listOf(MissionSurveyImpact.BASELINE_FREQUENCY),
        ),
        choice(
            MissionSurveyQuestionCode.LIVING_TRIGGER,
            "예정에 없던 소비는 주로 언제 발생하나요?",
            options(
                LivingSpendingTrigger.DISCOUNT_OR_LIMITED_SALE to "할인·한정판매",
                LivingSpendingTrigger.AD_OR_SOCIAL_MEDIA to "광고·SNS·추천",
                LivingSpendingTrigger.INVENTORY_UNCHECKED to "보유 재고를 확인하지 못했을 때",
                LivingSpendingTrigger.STRESS_OR_BOREDOM to "스트레스·무료함",
                LivingSpendingTrigger.CONVENIENCE to "편리함 때문에",
                LivingSpendingTrigger.FORGOT_AUTO_PAYMENT to "자동결제를 잊어서",
                LivingSpendingTrigger.RARELY_UNPLANNED to "예정에 없던 소비는 거의 없음",
            ),
            impacts = listOf(MissionSurveyImpact.MISSION_FILTER),
        ),
        multiChoice(
            MissionSurveyQuestionCode.LIVING_SAVING_METHODS,
            "어떤 절약 행동을 실천할 수 있나요?",
            options(
                LivingSavingMethod.WAIT_24_HOURS to "구매 전 24시간 기다리기",
                LivingSavingMethod.USE_SHOPPING_LIST to "쇼핑 목록 사용",
                LivingSavingMethod.CHECK_INVENTORY to "집에 있는 재고 확인",
                LivingSavingMethod.LIMIT_FREQUENCY to "구매·이용 횟수 제한",
                LivingSavingMethod.REVIEW_SUBSCRIPTIONS to "구독 이용 여부 점검",
                LivingSavingMethod.CONSIDER_REUSE to "수리·대여·중고·재사용 검토",
                LivingSavingMethod.EXCLUDE_NECESSARY_COST to "건강·교육 등 필요한 비용은 제외",
                LivingSavingMethod.NO_LIVING_MISSION to "해당 영역의 미션은 원하지 않음",
            ),
            maximum = LivingSavingMethod.entries.size,
            exclusive = listOf(LivingSavingMethod.NO_LIVING_MISSION),
            impacts = listOf(MissionSurveyImpact.ALTERNATIVE_ACTION, MissionSurveyImpact.EXCLUSION_CONDITION),
        ),
    )

    private fun choice(
        code: MissionSurveyQuestionCode,
        prompt: String,
        options: List<MissionSurveyOptionDefinition>,
        dependsOn: MissionSurveyQuestionCode? = null,
        skipWhen: List<MissionSurveyCode> = emptyList(),
        impacts: List<MissionSurveyImpact>,
    ): MissionSurveyQuestionDefinition =
        MissionSurveyQuestionDefinition(
            code = code.code,
            prompt = prompt,
            answerType = SurveyAnswerType.SINGLE_CHOICE,
            options = options,
            minSelections = 1,
            maxSelections = 1,
            dependsOnQuestionCode = dependsOn?.code,
            skipWhenOptionCodes = skipWhen.map(MissionSurveyCode::code),
            impacts = impacts,
        )

    private fun multiChoice(
        code: MissionSurveyQuestionCode,
        prompt: String,
        options: List<MissionSurveyOptionDefinition>,
        maximum: Int,
        exclusive: List<MissionSurveyCode> = emptyList(),
        conditionalOptionRules: List<MissionSurveyConditionalOptionRule> = emptyList(),
        impacts: List<MissionSurveyImpact>,
    ): MissionSurveyQuestionDefinition =
        MissionSurveyQuestionDefinition(
            code = code.code,
            prompt = prompt,
            answerType = SurveyAnswerType.MULTI_CHOICE,
            options = options,
            minSelections = 1,
            maxSelections = maximum,
            exclusiveOptionCodes = exclusive.map(MissionSurveyCode::code),
            conditionalOptionRules = conditionalOptionRules,
            impacts = impacts,
        )

    private fun number(
        code: MissionSurveyQuestionCode,
        prompt: String,
        dependsOn: MissionSurveyQuestionCode,
        skipWhen: List<MissionSurveyCode>,
        rules: List<MissionSurveyNumericRule>,
        impacts: List<MissionSurveyImpact>,
    ): MissionSurveyQuestionDefinition =
        numeric(code, prompt, SurveyAnswerType.NUMBER, dependsOn, skipWhen, rules, impacts)

    private fun keyedNumber(
        code: MissionSurveyQuestionCode,
        prompt: String,
        dependsOn: MissionSurveyQuestionCode,
        skipWhen: List<MissionSurveyCode>,
        rules: List<MissionSurveyNumericRule>,
        impacts: List<MissionSurveyImpact>,
    ): MissionSurveyQuestionDefinition =
        numeric(code, prompt, SurveyAnswerType.KEYED_NUMBER, dependsOn, skipWhen, rules, impacts)

    private fun numeric(
        code: MissionSurveyQuestionCode,
        prompt: String,
        type: SurveyAnswerType,
        dependsOn: MissionSurveyQuestionCode,
        skipWhen: List<MissionSurveyCode>,
        rules: List<MissionSurveyNumericRule>,
        impacts: List<MissionSurveyImpact>,
    ): MissionSurveyQuestionDefinition =
        MissionSurveyQuestionDefinition(
            code = code.code,
            prompt = prompt,
            answerType = type,
            dependsOnQuestionCode = dependsOn.code,
            skipWhenOptionCodes = skipWhen.map(MissionSurveyCode::code),
            numericRules = rules,
            impacts = impacts,
        )

    private fun options(vararg values: Pair<MissionSurveyCode, String>): List<MissionSurveyOptionDefinition> =
        values.map { (value, label) -> MissionSurveyOptionDefinition(value.code, label) }
}
