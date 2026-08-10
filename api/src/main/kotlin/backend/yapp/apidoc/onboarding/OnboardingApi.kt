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

@Tag(
    name = "Onboarding",
    description = "온보딩 플로우 API. <br>" +
            "사용자에게 생년월일·월급·월저축액·순자산·목표기간을 입력받아 저장하고, " +
        "이를 바탕으로 재무 분석 리포트와 2가지 목표 금액안을 산출한 뒤 목표를 확정한다. <br>" +
            "모든 API는 게스트 액세스 토큰 인증이 필요하다.",
)
@SecurityRequirement(name = "accessTokenAuth")
interface OnboardingApi {

    @Operation(
        summary = "온보딩 프로필 부분 저장",
        description = "온보딩 입력 스텝(나이 / 월급·저축 / 순자산 / 목표기간)에서 입력한 값을 부분 저장(upsert)한다. <br>" +
            "한 번에 전부 보내지 않고, 각 스텝을 넘어갈 때마다 그 스텝의 필드만 담아 호출한다.<br><br>" +
            "1/4 나이: birthDate<br>" +
            "2/4 월급·저축: monthlySalaryManwon, monthlySavingManwon<br>" +
            "3/4 순자산: netWorthManwon<br>" +
            "4/4 목표기간: goalPeriodMonths<br><br>" +
            "스텝마다 서버에 저장하므로 앱을 껐다 켜도 진행 상태가 복원된다. <br><br>" +
            "월저축액이 월급을 초과하면 400(INVALID_ONBOARDING_INPUT).<br><br> " +
            "온보딩이 완료(COMPLETED)된 뒤에는 수정할 수 없다. 완료 후 목표 금액·기간 변경은 Goal API를 사용한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "저장 성공", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
        ApiResponse(responseCode = "400", description = "INVALID_ONBOARDING_INPUT 또는 VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_ALREADY_COMPLETED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun patchProfile(guestUserId: Long, request: ProfilePatchRequest): ProfileResponse

    @Operation(
        summary = "온보딩 프로필 조회",
        description = "온보딩 화면 재진입 시(미완료 상태로 재접속 등) 지금까지 저장된 입력값과 진행 상태(IN_PROGRESS / COMPLETED)를 반환한다.<br>" +
            "클라이언트는 이 값으로 마지막에 머문 스텝부터 이어서 진행한다.<br><br>" +
            "아직 아무 입력도 저장되지 않았으면 모든 입력값이 null인 IN_PROGRESS 상태를 반환한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun getProfile(guestUserId: Long): ProfileResponse

    @Operation(
        summary = "재무 분석 리포트 조회",
        description = "'나는 잘하고 있을까?' 재무 분석 리포트 화면(onboarding_report) 데이터를 계산해 반환한다. <br>" +
            "저장된 프로필의 월급·월저축액·순자산·목표기간이 모두 있어야 하며, 하나라도 없으면 409(ONBOARDING_INCOMPLETE).<br><br>" +
            "응답 구성<br>" +
            "시뮬레이션: 현행 유지(baseline) vs 저축률 상향 시 예상 금액(simulation)과 그 차액. 연복리를 적용한 추정치이다.<br>" +
            "또래 비교: 순자산 중앙값 대비 비율, 소득·소비 상위 백분위. 입력 월급은 세후 실수령액이므로 세전으로 보정해 통계와 비교한다.<br>" +
            "히스토그램: 또래 소득·소비 분포와 사용자 위치 마커.<br>" +
            "종합 진단: 자산·소득·소비 3축 조합(8분기)에 따른 맞춤 진단·조언 문구.<br><br>" +
            "표시 금액 단위는 만원이며, 값은 단순 추정치이다(응답의 disclaimer 참고).",
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
        description = "'2가지 목표금액을 준비했어요' 목표 선택 화면(onboarding_goalselect) 데이터를 반환한다. <br>" +
            "저장된 월저축액·목표기간을 바탕으로 1안(확실하게)·2안(여유롭게) 두 목표안을 계산한다.<br><br>" +
            "각 안의 구성<br>" +
            "목표 증가분 범위: 기존 저축 대비 추가로 더 모을 금액 범위(min~max)<br>" +
            "기간별 체크포인트: 목표기간을 4등분한 시점별 목표 금액(막대그래프용)<br>" +
            "최종 카드: 목표기간 종료 시점의 목표 금액<br><br>" +
            "1안이 기본 선택이며, 월저축액·목표기간이 없으면 409(ONBOARDING_INCOMPLETE).",
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
        description = "목표 선택 화면의 '이 목표로 시작' 동작. 사용자가 고른 안(PLAN_1 확실하게 / PLAN_2 여유롭게)으로 " +
            "목표를 확정 저장하고, 온보딩을 완료(COMPLETED) 처리한다. <br>" +
                "확정된 목표 금액·기간을 반환한다. <br>" +
            "이미 온보딩을 완료했다면 409(ONBOARDING_ALREADY_COMPLETED). <br><br> " +
                "월저축액·목표기간이 없으면 409(ONBOARDING_INCOMPLETE).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "확정 성공", content = [Content(schema = Schema(implementation = GoalResponse::class))]),
        ApiResponse(responseCode = "400", description = "VALIDATION_FAILED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "404", description = "ONBOARDING_PROFILE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
        ApiResponse(responseCode = "409", description = "ONBOARDING_INCOMPLETE 또는 ONBOARDING_ALREADY_COMPLETED", content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))]),
    )
    fun confirmGoal(guestUserId: Long, request: GoalConfirmRequest): GoalResponse
}
