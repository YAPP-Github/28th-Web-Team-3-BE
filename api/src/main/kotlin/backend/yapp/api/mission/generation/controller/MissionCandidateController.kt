package backend.yapp.api.mission.generation.controller

import backend.yapp.api.mission.generation.dto.MissionCandidatesResponse
import backend.yapp.api.mission.generation.dto.MissionGenerationCreateRequest
import backend.yapp.apidoc.mission.generation.MissionCandidateApi
import backend.yapp.core.mission.generation.service.MissionCandidateService
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/missions/generations")
@ConditionalOnProperty(prefix = "app", name = ["role"], havingValue = "api", matchIfMissing = true)
class MissionCandidateController(
    private val service: MissionCandidateService,
) : MissionCandidateApi {
    @PostMapping
    override fun candidates(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: MissionGenerationCreateRequest,
    ): MissionCandidatesResponse =
        MissionCandidatesResponse.from(
            service.candidates(
                guestUserId = guestUserId,
                category = request.category,
                item = request.item,
                baselineFrequency = request.baselineFrequency,
                baselineAmountWon = request.baselineAmountWon,
            ),
        )
}
