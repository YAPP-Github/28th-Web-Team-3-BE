package backend.yapp.apidoc.mission.generation

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.mission.generation.dto.MissionCandidatesResponse
import backend.yapp.api.mission.generation.dto.MissionGenerationCreateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(
    name = "Mission Candidates",
    description = "동기식 미션 후보 조회 API",
)
@SecurityRequirement(name = "accessTokenAuth")
interface MissionCandidateApi {
    @Operation(
        summary = "미션 후보 3개 생성",
        description = "카테고리·항목·주간 빈도·주간 금액을 입력하면 저장이나 polling 없이 미션 후보 3개를 즉시 반환한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "미션 후보 조회 성공",
            content = [Content(schema = Schema(implementation = MissionCandidatesResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "MISSION_GENERATION_INPUT_INVALID",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "ONBOARDING_INCOMPLETE",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
    )
    fun candidates(guestUserId: Long, request: MissionGenerationCreateRequest): MissionCandidatesResponse
}
