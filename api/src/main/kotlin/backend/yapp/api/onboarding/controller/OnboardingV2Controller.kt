package backend.yapp.api.onboarding.controller

import backend.yapp.api.onboarding.dto.GoalConfirmV2Request
import backend.yapp.api.onboarding.dto.GoalPreviewResponse
import backend.yapp.api.onboarding.dto.GoalV2Response
import backend.yapp.apidoc.onboarding.OnboardingV2Api
import backend.yapp.core.onboarding.service.OnboardingGoalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/onboarding")
class OnboardingV2Controller(
    private val goalService: OnboardingGoalService,
) : OnboardingV2Api {

    @GetMapping("/goal-preview")
    override fun goalPreview(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) monthlySavingManwon: Int?,
    ): GoalPreviewResponse =
        GoalPreviewResponse.from(goalService.previewV2(guestUserId, monthlySavingManwon))

    @PostMapping("/goal")
    @ResponseStatus(HttpStatus.CREATED)
    override fun confirmGoal(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: GoalConfirmV2Request,
    ): GoalV2Response =
        GoalV2Response.from(goalService.confirmV2(guestUserId, request.monthlySavingManwon))
}
