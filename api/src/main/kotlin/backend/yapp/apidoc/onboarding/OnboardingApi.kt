package backend.yapp.apidoc.onboarding

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.onboarding.dto.GoalConfirmRequest
import backend.yapp.api.onboarding.dto.GoalPlansResponse
import backend.yapp.api.onboarding.dto.GoalResponse
import backend.yapp.api.onboarding.dto.ProfilePatchRequest
import backend.yapp.api.onboarding.dto.ProfileResponse
import backend.yapp.api.onboarding.dto.ReportResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Onboarding", description = "온보딩 입력 저장 및 재무 리포트·목표 금액 산출 API")
@SecurityRequirement(name = "accessTokenAuth")
interface OnboardingApi {

    @Operation(
        summary = "온보딩 프로필 부분 저장",
        description = "온보딩 스텝별로 입력한 필드만 upsert 한다. 월저축액이 월급을 초과하면 400.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "저장 성공", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_ONBOARDING_INPUT 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun patchProfile(guestUserId: Long, request: ProfilePatchRequest): ProfileResponse

    @Operation(
        summary = "온보딩 프로필 조회",
        description = "재접속 시 저장된 입력값과 진행 상태를 반환한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun getProfile(guestUserId: Long): ProfileResponse

    @Operation(
        summary = "재무 분석 리포트 조회",
        description = "저장된 프로필로 시뮬레이션·또래 백분위·히스토그램·종합 분석을 계산한다. 필수 입력이 없으면 409.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ReportResponse::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun report(guestUserId: Long): ReportResponse

    @Operation(
        summary = "목표 금액안 조회",
        description = "월저축액·목표기간 기준으로 1안/2안 목표 금액과 기간별 체크포인트를 산출한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = GoalPlansResponse::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun goalPlans(guestUserId: Long): GoalPlansResponse

    @Operation(
        summary = "목표 확정",
        description = "선택한 안으로 목표를 확정하고 온보딩을 완료 처리한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "확정 성공", content = [Content(schema = Schema(implementation = GoalResponse::class))]),
        ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun confirmGoal(guestUserId: Long, request: GoalConfirmRequest): GoalResponse
}
