package backend.yapp.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val httpStatus: HttpStatus,
    val code: Int,
    val message: String,
) {
    // Global
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500, "서버 오류가 발생했습니다."),
    MISSING_PART(HttpStatus.BAD_REQUEST, 400, "요청에 필요한 부분이 없습니다."),
    NO_HANDLER_FOUND(HttpStatus.NOT_FOUND, 404, "요청하신 API가 존재하지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 405, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 415, "지원하지 않는 Content-Type입니다."),

    // Validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, 400, "요청한 값이 올바르지 않습니다."),

    // Authentication
    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, 400, "식별값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 401, "인증이 필요하거나 토큰이 유효하지 않습니다."),

    // Onboarding
    INVALID_ONBOARDING_INPUT(HttpStatus.BAD_REQUEST, 400, "온보딩 입력값이 올바르지 않습니다."),
    ONBOARDING_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, 404, "온보딩 정보가 없습니다."),
    ONBOARDING_INCOMPLETE(HttpStatus.CONFLICT, 409, "온보딩 입력이 완료되지 않았습니다."),

    // Mission survey
    MISSION_SURVEY_INVALID(HttpStatus.BAD_REQUEST, 400, "미션 설문 응답이 올바르지 않습니다."),
    MISSION_SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, 404, "저장된 미션 설문을 찾을 수 없습니다."),
    MISSION_SURVEY_CONFLICT(HttpStatus.CONFLICT, 409, "미션 설문이 동시에 변경되었습니다. 다시 시도해 주세요."),

    // Mission generation
    MISSION_GENERATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, 404, "미션 생성 작업을 찾을 수 없습니다."),
    MISSION_GENERATION_NOT_READY(HttpStatus.CONFLICT, 409, "미션 생성 작업이 아직 완료되지 않았습니다."),
    MISSION_GENERATION_FAILED(HttpStatus.CONFLICT, 409, "미션 생성 작업이 실패했습니다. 다시 요청해 주세요."),
    MISSION_DRAFT_EXPIRED(HttpStatus.CONFLICT, 409, "미션 초안이 만료되었습니다. 다시 생성해 주세요."),
    MISSION_CONFIRM_INVALID(HttpStatus.BAD_REQUEST, 400, "선택한 미션 초안이 올바르지 않습니다."),
    MISSION_CONFIRM_CONFLICT(HttpStatus.CONFLICT, 409, "이미 다른 미션 선택으로 시작했습니다."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, 404, "미션을 찾을 수 없습니다."),
    MISSION_STATUS_CONFLICT(HttpStatus.CONFLICT, 409, "현재 상태에서는 미션을 완료할 수 없습니다."),
    MANUAL_MISSION_INVALID(HttpStatus.BAD_REQUEST, 400, "수동 미션 입력값이 올바르지 않습니다."),
}
