package backend.yapp.apidoc.mission.survey

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.mission.survey.dto.MissionSurveyPutRequest
import backend.yapp.api.mission.survey.dto.MissionSurveyQuestionsResponse
import backend.yapp.api.mission.survey.dto.MissionSurveyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(
    name = "Mission Surveys",
    description = "미션 생성을 위한 관심 카테고리별 설문 문항 조회와 전체 응답 저장·조회 API. 모든 API는 게스트 액세스 토큰 인증이 필요하다.",
)
@SecurityRequirement(name = "accessTokenAuth")
interface MissionSurveyApi {
    @Operation(
        summary = "선택 카테고리 설문 문항 조회",
        description = "관심 카테고리 1~4개에 해당하는 문항만 고정 순서로 반환한다. " +
            "카테고리는 `categories=MEAL&categories=TRANSPORT`처럼 반복하거나 comma-separated로 보낼 수 있다. " +
            "각 카테고리에는 최대 5개 문항과 분기, 숫자 단위·범위, 배타 선택지, 미션 활용 목적이 포함된다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "문항 조회 성공", content = [Content(
            schema = Schema(implementation = MissionSurveyQuestionsResponse::class),
            examples = [ExampleObject(value = QUESTIONS_EXAMPLE)],
        )]),
        ApiResponse(responseCode = "400", description = "MISSION_SURVEY_INVALID", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun questions(
        guestUserId: Long,
        @Parameter(
            description = "관심 카테고리. MEAL, TRANSPORT, HOBBY, LIVING 중 중복 없이 1~4개.",
            example = "MEAL,TRANSPORT",
        )
        categories: List<String>?,
    ): MissionSurveyQuestionsResponse

    @Operation(
        summary = "저장된 전체 미션 설문 조회",
        description = "마지막 PUT으로 저장한 전체 카테고리 응답을 canonical 순서로 반환한다. 미선택 카테고리는 null이다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "설문 조회 성공", content = [Content(
            schema = Schema(implementation = MissionSurveyResponse::class),
            examples = [ExampleObject(value = SURVEY_EXAMPLE)],
        )]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "MISSION_SURVEY_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun get(guestUserId: Long): MissionSurveyResponse

    @Operation(
        summary = "전체 미션 설문 저장·교체",
        description = """
            meal, transport, hobby, living 중 선택한 1~4개 카테고리의 완전한 응답을 한 번에 저장한다.

            클라이언트는 (1) 카테고리를 선택하고, (2) 선택한 모든 카테고리로 질문 API를 한 번 호출한 뒤,
            (3) 응답 option의 `code`를 그대로 form state에 저장한다. 화면은 카테고리별로 순차 표시할 수 있지만,
            (4) 마지막에는 같은 code를 사용해 선택한 모든 카테고리 응답을 한 번의 PUT으로 제출한다.

            분기 규칙:
            - meal.target이 UNKNOWN이면 weeklyFrequencyRange와 reason을 생략하거나 null로 보낸다.
            - transport.target이 UNKNOWN이면 weeklyFrequencyRange만 생략하거나 null로 보내며 reason은 필수다.
            - hobby.hobbies에 OTHER가 포함되면 otherHobby에 앞뒤 공백을 제외한 1~50자 텍스트를 보내고,
              OTHER가 없으면 otherHobby를 생략하거나 null로 보낸다.
            - hobby.spendingTypes가 [DO_NOT_REDUCE]이면 monthlySpendingRange를 생략하거나 null로,
              frequencies를 빈 배열로, savingMethods를 [NO_HOBBY_MISSION]으로 보낸다.
            - 일반 취미 분기에서 NO_HOBBY_MISSION은 다른 savingMethods와 함께 보낼 수 없다.
            - living.areas가 [UNKNOWN]이면 monthlySpendingRange를 생략하거나 null로 보내고 frequencies는 빈 배열로 보낸다.
            - UNKNOWN, DO_NOT_REDUCE, NONE, NO_ALTERNATIVE, NO_HOBBY_MISSION, NO_LIVING_MISSION 같은
              배타 옵션은 해당 필드의 다른 옵션과 함께 보낼 수 없다.
            - hobby.frequencies의 spendingType 집합은 hobby.spendingTypes와, living.frequencies의 area 집합은
              living.areas와 정확히 일치해야 하며 각 key는 한 번만 등장한다.

            기존 설문이 있으면 요청에 없는 카테고리와 이전 답변까지 포함해 전체 상태를 원자적으로 교체한다.
            검증 실패 시 기존 상태는 유지되며, 성공 응답은 직후 GET과 동일하다.
        """,
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                schema = Schema(implementation = MissionSurveyPutRequest::class),
                examples = [
                    ExampleObject(
                        name = "allCategories",
                        summary = "일반적인 4개 카테고리 전체 요청",
                        value = ALL_CATEGORIES_REQUEST_EXAMPLE,
                    ),
                    ExampleObject(
                        name = "singleCategory",
                        summary = "최소 1개 카테고리 요청",
                        value = SINGLE_CATEGORY_REQUEST_EXAMPLE,
                    ),
                    ExampleObject(
                        name = "mealAndTransportUnknown",
                        summary = "식사·교통 UNKNOWN 분기",
                        value = MEAL_TRANSPORT_UNKNOWN_REQUEST_EXAMPLE,
                    ),
                    ExampleObject(
                        name = "hobbyDoNotReduce",
                        summary = "취미 DO_NOT_REDUCE 분기",
                        value = HOBBY_DO_NOT_REDUCE_REQUEST_EXAMPLE,
                    ),
                    ExampleObject(
                        name = "hobbyOther",
                        summary = "기타 취미 주관식 입력",
                        value = HOBBY_OTHER_REQUEST_EXAMPLE,
                    ),
                    ExampleObject(
                        name = "keyedFrequencies",
                        summary = "취미·생활 keyed frequency 요청",
                        value = KEYED_FREQUENCIES_REQUEST_EXAMPLE,
                    ),
                ],
            )],
        ),
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "전체 설문 저장·교체 성공", content = [Content(
            schema = Schema(implementation = MissionSurveyResponse::class),
            examples = [ExampleObject(value = SURVEY_EXAMPLE)],
        )]),
        ApiResponse(responseCode = "400", description = "MISSION_SURVEY_INVALID 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "MISSION_SURVEY_CONFLICT", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun replace(guestUserId: Long, request: MissionSurveyPutRequest): MissionSurveyResponse

    companion object {
        private const val ALL_CATEGORIES_REQUEST_EXAMPLE = """
            {
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequencyRange": "THREE_TO_FOUR",
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "TAXI",
                "target": "TAXI",
                "weeklyFrequencyRange": "ONE_TO_TWO",
                "reason": "TIME_PRESSURE",
                "exclusions": ["NONE"]
              },
              "hobby": {
                "hobbies": ["READING", "GAME"],
                "spendingTypes": ["SUBSCRIPTION", "GOODS"],
                "monthlySpendingRange": "FROM_50K_TO_150K",
                "frequencies": [
                  {"spendingType": "SUBSCRIPTION", "count": 2},
                  {"spendingType": "GOODS", "count": 3}
                ],
                "savingMethods": ["REVIEW_SUBSCRIPTIONS", "WAIT_BEFORE_BUYING"]
              },
              "living": {
                "areas": ["SUBSCRIPTION", "ONLINE_SHOPPING"],
                "monthlySpendingRange": "FROM_30K_TO_100K",
                "frequencies": [
                  {"area": "SUBSCRIPTION", "count": 3},
                  {"area": "ONLINE_SHOPPING", "count": 4}
                ],
                "trigger": "DISCOUNT_OR_LIMITED_SALE",
                "savingMethods": ["REVIEW_SUBSCRIPTIONS", "WAIT_24_HOURS"]
              }
            }
        """

        private const val SINGLE_CATEGORY_REQUEST_EXAMPLE = """
            {
              "meal": {
                "target": "UNKNOWN",
                "alternatives": ["NO_ALTERNATIVE"],
                "exclusions": ["NONE"]
              }
            }
        """

        private const val MEAL_TRANSPORT_UNKNOWN_REQUEST_EXAMPLE = """
            {
              "meal": {
                "target": "UNKNOWN",
                "alternatives": ["NO_ALTERNATIVE"],
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "VARIES",
                "target": "UNKNOWN",
                "reason": "CONVENIENCE_OR_HABIT",
                "exclusions": ["NONE"]
              }
            }
        """

        private const val HOBBY_DO_NOT_REDUCE_REQUEST_EXAMPLE = """
            {
              "hobby": {
                "hobbies": ["READING"],
                "spendingTypes": ["DO_NOT_REDUCE"],
                "frequencies": [],
                "savingMethods": ["NO_HOBBY_MISSION"]
              }
            }
        """

        private const val HOBBY_OTHER_REQUEST_EXAMPLE = """
            {
              "hobby": {
                "hobbies": ["READING", "OTHER"],
                "otherHobby": "보드게임",
                "spendingTypes": ["GOODS"],
                "monthlySpendingRange": "UNDER_50K",
                "frequencies": [
                  {"spendingType": "GOODS", "count": 2}
                ],
                "savingMethods": ["WAIT_BEFORE_BUYING"]
              }
            }
        """

        private const val KEYED_FREQUENCIES_REQUEST_EXAMPLE = """
            {
              "hobby": {
                "hobbies": ["MOVIE_OR_OTT"],
                "spendingTypes": ["SUBSCRIPTION", "TICKET"],
                "monthlySpendingRange": "FROM_50K_TO_150K",
                "frequencies": [
                  {"spendingType": "SUBSCRIPTION", "count": 2},
                  {"spendingType": "TICKET", "count": 1}
                ],
                "savingMethods": ["REVIEW_SUBSCRIPTIONS"]
              },
              "living": {
                "areas": ["SUBSCRIPTION", "CLOTHING"],
                "monthlySpendingRange": "FROM_100K_TO_300K",
                "frequencies": [
                  {"area": "SUBSCRIPTION", "count": 3},
                  {"area": "CLOTHING", "count": 2}
                ],
                "trigger": "AD_OR_SOCIAL_MEDIA",
                "savingMethods": ["WAIT_24_HOURS"]
              }
            }
        """

        private const val SURVEY_EXAMPLE = """
            {
              "schemaVersion": "V3",
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequencyRange": "THREE_TO_FOUR",
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "TAXI",
                "target": "TAXI",
                "weeklyFrequencyRange": "ONE_TO_TWO",
                "reason": "TIME_PRESSURE",
                "exclusions": ["NONE"]
              },
              "hobby": null,
              "living": null
            }
        """

        private const val QUESTIONS_EXAMPLE = """
            {
              "categories": [{
                "category": "MEAL",
                "questions": [{
                  "code": "MEAL_FREQUENCY",
                  "prompt": "선택한 항목을 평소 한 주에 몇 번 이용하나요?",
                  "answerType": "NUMBER",
                  "options": [],
                  "minSelections": null,
                  "maxSelections": null,
                  "dependsOnQuestionCode": "MEAL_TARGET",
                  "skipWhenOptionCodes": ["UNKNOWN"],
                  "numericRules": [{
                    "subjectOptionCode": "DELIVERY",
                    "unit": "TIMES_PER_WEEK",
                    "minimum": 0,
                    "maximum": 7
                  }],
                  "textRules": [],
                  "exclusiveOptionCodes": [],
                  "conditionalOptionRules": [],
                  "impacts": ["BASELINE_FREQUENCY"]
                }]
              }]
            }
        """
    }
}
