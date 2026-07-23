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
        description = "meal, transport, hobby, living 중 선택한 1~4개 카테고리의 완전한 응답을 한 번에 저장한다. " +
            "기존 설문이 있으면 요청에 없는 카테고리와 이전 답변까지 포함해 전체 상태를 원자적으로 교체한다. " +
            "검증 실패 시 기존 상태는 유지되며, 성공 응답은 직후 GET과 동일하다.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                schema = Schema(implementation = MissionSurveyPutRequest::class),
                examples = [ExampleObject(value = SURVEY_REQUEST_EXAMPLE)],
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
        private const val SURVEY_REQUEST_EXAMPLE = """
            {
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequency": 3,
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "TAXI",
                "target": "TAXI",
                "weeklyFrequency": 2,
                "reason": "TIME_PRESSURE",
                "exclusions": ["NONE"]
              },
              "hobby": null,
              "living": null
            }
        """

        private const val SURVEY_EXAMPLE = """
            {
              "schemaVersion": "V1",
              "meal": {
                "target": "DELIVERY",
                "weeklyFrequency": 3,
                "alternatives": ["COOK", "PICKUP"],
                "reason": "TIME_OR_ENERGY",
                "exclusions": ["NONE"]
              },
              "transport": {
                "primaryMode": "TAXI",
                "target": "TAXI",
                "weeklyFrequency": 2,
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
                  "exclusiveOptionCodes": [],
                  "conditionalOptionRules": [],
                  "impacts": ["BASELINE_FREQUENCY"]
                }]
              }]
            }
        """
    }
}
