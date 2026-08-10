package backend.yapp.api.mission.generation.controller

import backend.yapp.api.mission.generation.dto.MissionConfirmRequest
import backend.yapp.api.mission.generation.dto.MissionConfirmResponse
import backend.yapp.api.mission.generation.dto.MissionDraftsResponse
import backend.yapp.api.mission.generation.dto.MissionGenerationJobResponse
import backend.yapp.api.mission.generation.dto.MissionGenerationCreateRequest
import backend.yapp.apidoc.mission.generation.MissionGenerationApi
import backend.yapp.core.mission.generation.service.MissionGenerationService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/missions/generation-jobs")
@ConditionalOnProperty(prefix = "app", name = ["role"], havingValue = "api", matchIfMissing = true)
class MissionGenerationController(
    private val service: MissionGenerationService,
) : MissionGenerationApi {
    @PostMapping
    override fun request(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: MissionGenerationCreateRequest,
    ): ResponseEntity<MissionGenerationJobResponse> =
        ResponseEntity.accepted().body(
            MissionGenerationJobResponse.from(
                service.request(
                    guestUserId = guestUserId,
                    category = request.category,
                    item = request.item,
                    baselineFrequency = request.baselineFrequency,
                    baselineAmountWon = request.baselineAmountWon,
                ),
            ),
        )

    @GetMapping("/{jobId}")
    override fun status(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable jobId: UUID,
    ): MissionGenerationJobResponse =
        MissionGenerationJobResponse.from(service.status(guestUserId, jobId))

    @GetMapping("/{jobId}/drafts")
    override fun drafts(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable jobId: UUID,
    ): MissionDraftsResponse =
        MissionDraftsResponse.from(jobId, service.drafts(guestUserId, jobId))

    @PostMapping("/{jobId}/confirm")
    override fun confirm(
        @AuthenticationPrincipal guestUserId: Long,
        @PathVariable jobId: UUID,
        @Valid @RequestBody request: MissionConfirmRequest,
    ): MissionConfirmResponse =
        MissionConfirmResponse.from(
            jobId,
            service.confirm(guestUserId, jobId, request.selectedDraftIds),
        )
}
