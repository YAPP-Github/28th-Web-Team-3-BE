package backend.yapp.api.mission.lifecycle.controller

import backend.yapp.api.mission.lifecycle.dto.ManualMissionCreateRequest
import backend.yapp.api.mission.lifecycle.dto.MissionHistoriesResponse
import backend.yapp.api.mission.lifecycle.dto.MissionHistoryPeriodParser
import backend.yapp.api.mission.lifecycle.dto.MissionLifecycleResponse
import backend.yapp.api.mission.lifecycle.dto.MissionsResponse
import backend.yapp.api.mission.lifecycle.dto.MissionProgressResponse
import backend.yapp.api.mission.lifecycle.dto.MissionCatalogResponse
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.service.MissionLifecycleService
import backend.yapp.core.mission.generation.service.MissionHistoryService
import backend.yapp.core.mission.generation.service.MissionSource
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/missions")
@Tag(name = "Mission Lifecycle", description = "추천·수동 미션 조회, 생성, 완료")
class MissionController(
    private val service: MissionLifecycleService,
    private val historyService: MissionHistoryService,
) {
    @GetMapping("/catalog")
    @Operation(summary = "미션 카테고리·항목 목록 조회")
    fun catalog(): MissionCatalogResponse = MissionCatalogResponse.create()

    @GetMapping
    @Operation(summary = "내 미션 조회")
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) status: MissionStatus?,
        @RequestParam(required = false) category: MissionCategory?,
    ): MissionsResponse = MissionsResponse.from(service.list(guestUserId, status, category))

    @GetMapping("/progress")
    @Operation(summary = "이번 주 미션 진행률 조회")
    fun progress(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) category: MissionCategory?,
    ): MissionProgressResponse = MissionProgressResponse.from(service.progress(guestUserId, category))

    @GetMapping("/histories")
    @Operation(
        summary = "월별·주차별 미션 완료 히스토리 조회",
        description = "선택 월의 모든 주차를 반환합니다. 2026년 8월 1~2주차와 아직 시작하지 않은 주차는 0/0입니다.",
    )
    fun histories(
        @AuthenticationPrincipal guestUserId: Long,
        @Parameter(required = true, schema = Schema(type = "integer", format = "int32"), example = "2026")
        @RequestParam(required = false) year: String?,
        @Parameter(required = true, schema = Schema(type = "integer", format = "int32"), example = "8")
        @RequestParam(required = false) month: String?,
    ): MissionHistoriesResponse = MissionHistoriesResponse.from(
        historyService.histories(guestUserId, MissionHistoryPeriodParser.parse(year, month)),
    )

    @PostMapping("/manual")
    @Operation(summary = "수동 미션 생성")
    @ResponseStatus(HttpStatus.CREATED)
    fun createManual(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: ManualMissionCreateRequest,
    ): MissionLifecycleResponse = MissionLifecycleResponse.from(
        service.createManual(
            guestUserId,
            request.category,
            request.text,
        ),
    )

    @DeleteMapping("/{source}/{missionId}")
    @Operation(summary = "미션 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable source: MissionSource,
        @PathVariable missionId: UUID,
    ) {
        service.delete(guestUserId, source, missionId)
    }

    @PatchMapping("/{source}/{missionId}/complete")
    @Operation(summary = "미션 완료")
    fun complete(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable source: MissionSource,
        @PathVariable missionId: UUID,
    ): MissionLifecycleResponse =
        MissionLifecycleResponse.from(service.complete(guestUserId, source, missionId))
}
