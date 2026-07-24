package backend.yapp.apidoc.mission.generation

import backend.yapp.api.global.exception.ErrorResponseEntity
import backend.yapp.api.mission.generation.dto.MissionConfirmRequest
import backend.yapp.api.mission.generation.dto.MissionConfirmResponse
import backend.yapp.api.mission.generation.dto.MissionDraftsResponse
import backend.yapp.api.mission.generation.dto.MissionGenerationJobResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID
import org.springframework.http.ResponseEntity

@Tag(
    name = "Mission Generation",
    description = "비동기 미션 초안 생성 job, polling, 초안 조회와 미션 시작 확정 API",
)
@SecurityRequirement(name = "accessTokenAuth")
interface MissionGenerationApi {
    @Operation(
        summary = "미션 생성 job 요청",
        description = "온보딩과 미션 설문 완료 후 호출한다. 진행 중인 job이 있으면 같은 job을 반환한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "생성 job 접수",
            content = [Content(schema = Schema(implementation = MissionGenerationJobResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "ONBOARDING_INCOMPLETE",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
    )
    fun request(guestUserId: Long): ResponseEntity<MissionGenerationJobResponse>

    @Operation(summary = "미션 생성 job 상태 polling")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "상태 조회 성공",
            content = [Content(schema = Schema(implementation = MissionGenerationJobResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "MISSION_GENERATION_JOB_NOT_FOUND",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
    )
    fun status(guestUserId: Long, jobId: UUID): MissionGenerationJobResponse

    @Operation(summary = "완료된 job의 카테고리별 미션 초안 조회")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "초안 조회 성공",
            content = [Content(schema = Schema(implementation = MissionDraftsResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "MISSION_GENERATION_NOT_READY, MISSION_GENERATION_FAILED 또는 MISSION_DRAFT_EXPIRED",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
    )
    fun drafts(guestUserId: Long, jobId: UUID): MissionDraftsResponse

    @Operation(
        summary = "미션 초안 선택 확정",
        description = "전체 초안 중 중복 없이 1개 이상을 ACTIVE 미션으로 저장한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "미션 시작 성공",
            content = [Content(schema = Schema(implementation = MissionConfirmResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "MISSION_CONFIRM_INVALID",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "MISSION_CONFIRM_CONFLICT 또는 job 상태·만료 오류",
            content = [Content(schema = Schema(implementation = ErrorResponseEntity::class))],
        ),
    )
    fun confirm(guestUserId: Long, jobId: UUID, request: MissionConfirmRequest): MissionConfirmResponse
}
