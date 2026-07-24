package backend.yapp.api.mission.lifecycle.controller

import backend.yapp.api.mission.lifecycle.dto.ManualMissionCreateRequest
import backend.yapp.api.mission.lifecycle.dto.MissionLifecycleResponse
import backend.yapp.api.mission.lifecycle.dto.MissionsResponse
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.service.MissionLifecycleService
import backend.yapp.core.mission.generation.service.MissionSource
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
) {
    @GetMapping
    @Operation(summary = "내 미션 조회")
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) status: MissionStatus?,
    ): MissionsResponse = MissionsResponse.from(service.list(guestUserId, status))

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
            request.targetCount,
            request.targetUnit,
        ),
    )

    @PatchMapping("/{source}/{missionId}/complete")
    @Operation(summary = "미션 완료")
    fun complete(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable source: MissionSource,
        @PathVariable missionId: UUID,
    ): MissionLifecycleResponse =
        MissionLifecycleResponse.from(service.complete(guestUserId, source, missionId))
}
