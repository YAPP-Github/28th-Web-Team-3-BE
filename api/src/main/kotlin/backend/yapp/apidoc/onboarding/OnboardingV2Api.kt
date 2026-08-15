package backend.yapp.apidoc.onboarding

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.onboarding.dto.GoalConfirmV2Request
import backend.yapp.api.onboarding.dto.GoalPreviewResponse
import backend.yapp.api.onboarding.dto.GoalV2Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(
    name = "Onboarding V2",
    description = "온보딩 목표 설정 V2 API. <br>" +
        "PLAN(1안/2안) 개념을 제거하고, 온보딩 입력 후 '얼마를 목표로 저축할까요?' 슬라이더 화면에서 " +
        "매달 모을 금액을 직접 골라 목표를 확정하는 플로우. <br>" +
        "모든 API는 게스트 액세스 토큰 인증이 필요하다.",
)
@SecurityRequirement(name = "accessTokenAuth")
interface OnboardingV2Api {

    @Operation(
        summary = "(v2) 목표 저축 미리보기(슬라이더)",
        description = "'얼마를 목표로 저축할까요?' 화면. 슬라이더로 고른 매달 모을 금액(monthlySavingManwon)으로 " +
            "저축 예상 금액을 재계산해 반환한다. <br>" +
            "예상 금액 = 순자산(baseAmountManwon) + 추가 저축액(매달 모을 금액 × 목표기간). <br>" +
            "슬라이더 범위: min(=현재 저축액) ~ max, max = MIN(현재 저축액 × 1.5, 월급). <br>" +
            "기본 위치(recommendedMonthlySavingManwon)는 '현재 저축액 + 권장 상향폭'이며 max로 clamp된다. <br>" +
            "monthlySavingManwon 미지정 시 기본 위치로 계산한다. <br>" +
            "현재 저축액 미만이거나 max 초과면 400, 월저축액·목표기간이 없으면 409(ONBOARDING_INCOMPLETE).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = GoalPreviewResponse::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_ONBOARDING_INPUT", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun goalPreview(guestUserId: Long, monthlySavingManwon: Int?): GoalPreviewResponse

    @Operation(
        summary = "(v2) 목표 확정",
        description = "'이 목표로 시작' 동작. 슬라이더로 고른 매달 모을 금액(monthlySavingManwon, 필수)으로 목표를 확정하고 " +
            "온보딩을 완료(COMPLETED) 처리한다. plan 입력값은 없다. <br>" +
            "목표 금액 = 순자산 + (매달 모을 금액 × 목표기간). <br>" +
            "금액은 현재 저축액 이상 ~ MIN(현재 저축액 × 1.5, 월급) 이하여야 하며, 범위 밖이면 400(INVALID_ONBOARDING_INPUT). <br>" +
            "확정된 목표 금액·기간을 반환한다. <br>" +
            "이미 온보딩을 완료했다면 409(ONBOARDING_ALREADY_COMPLETED). 월저축액·목표기간이 없으면 409(ONBOARDING_INCOMPLETE).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "확정 성공", content = [Content(schema = Schema(implementation = GoalV2Response::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_ONBOARDING_INPUT 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE 또는 ONBOARDING_ALREADY_COMPLETED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun confirmGoal(guestUserId: Long, request: GoalConfirmV2Request): GoalV2Response
}
