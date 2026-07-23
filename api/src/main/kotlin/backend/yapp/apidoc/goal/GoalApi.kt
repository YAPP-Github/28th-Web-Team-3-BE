package backend.yapp.apidoc.goal

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.goal.dto.GoalStatusResponse
import backend.yapp.api.goal.dto.GoalUpdateRequest
import backend.yapp.api.goal.dto.SavingRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(
    name = "Goal",
    description = "온보딩 확정 이후의 목표 추적 API. 전체 목표 대비 총 저축액·진행률, 서비스 사용기간·목표일 D-day, " +
        "이번 달 저축 목표 현황을 제공하고, 저축액 입력(덮어쓰기)과 목표 금액·기간 수정을 처리한다. 게스트 액세스 토큰 인증 필요.",
)
@SecurityRequirement(name = "accessTokenAuth")
interface GoalApi {

    @Operation(
        summary = "목표 현황 조회",
        description = "홈·목표 상세 화면 데이터를 반환한다.<br><br>" +
            "전체: 목표 금액, 총 저축액(온보딩 순자산 + 누적 저축), 진행률(%, 100 캡), 서비스 사용기간(개월), 목표일 D-day<br>" +
            "이번 달: 목표 금액(온보딩 월저축), 이번 달 저축액, 진행률, 말일까지 D-day<br><br>" +
            "온보딩 목표가 확정되지 않았으면 409. 최초 조회 시 온보딩 확정 데이터로 목표가 생성된다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = GoalStatusResponse::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "GOAL_ONBOARDING_REQUIRED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun get(guestUserId: Long): GoalStatusResponse

    @Operation(
        summary = "현재 저축액 입력",
        description = "이번 달 저축액을 입력값으로 덮어쓴다(set).<br>" +
                "총 저축액(온보딩 순자산 + 월별 합)에 반영되며, 갱신된 현황을 반환한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "입력 성공", content = [Content(schema = Schema(implementation = GoalStatusResponse::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_GOAL_INPUT 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "GOAL_ONBOARDING_REQUIRED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun setSaving(guestUserId: Long, request: SavingRequest): GoalStatusResponse

    @Operation(
        summary = "목표 금액/기간 수정",
        description = "전체 목표 금액과 목표 기간을 수정한다(변경할 필드만 전송).<br>" +
                "갱신된 현황을 반환한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = GoalStatusResponse::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_GOAL_INPUT 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "GOAL_ONBOARDING_REQUIRED 또는 GOAL_CONFLICT", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun update(guestUserId: Long, request: GoalUpdateRequest): GoalStatusResponse
}
