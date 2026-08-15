package backend.yapp.api.onboarding.controller

import backend.yapp.api.onboarding.dto.GoalConfirmRequest
import backend.yapp.api.onboarding.dto.GoalPlansResponse
import backend.yapp.api.onboarding.dto.GoalPreviewResponse
import backend.yapp.api.onboarding.dto.GoalResponse
import backend.yapp.api.onboarding.dto.ProfilePatchRequest
import backend.yapp.api.onboarding.dto.ProfileResponse
import backend.yapp.api.onboarding.dto.ReportResponse
import backend.yapp.apidoc.onboarding.OnboardingApi
import backend.yapp.core.onboarding.service.OnboardingGoalService
import backend.yapp.core.onboarding.service.OnboardingProfileService
import backend.yapp.core.onboarding.service.OnboardingReportService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/onboarding")
class OnboardingController(
    private val profileService: OnboardingProfileService,
    private val reportService: OnboardingReportService,
    private val goalService: OnboardingGoalService,
) : OnboardingApi {

    @PatchMapping("/profile")
    override fun patchProfile(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: ProfilePatchRequest,
    ): ProfileResponse =
        ProfileResponse.from(profileService.patch(guestUserId, request.toCommand()))

    @PutMapping("/profile")
    override fun updateProfile(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: ProfilePatchRequest,
    ): ProfileResponse =
        ProfileResponse.from(profileService.update(guestUserId, request.toCommand()))

    @GetMapping("/profile")
    override fun getProfile(@AuthenticationPrincipal guestUserId: Long): ProfileResponse =
        ProfileResponse.from(profileService.get(guestUserId))

    @GetMapping("/report")
    override fun report(@AuthenticationPrincipal guestUserId: Long): ReportResponse =
        ReportResponse.from(reportService.report(guestUserId))

    @GetMapping("/goal-plans")
    override fun goalPlans(@AuthenticationPrincipal guestUserId: Long): GoalPlansResponse =
        GoalPlansResponse.from(goalService.plans(guestUserId))

    @GetMapping("/goal-preview")
    override fun goalPreview(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) monthlySavingManwon: Int?,
    ): GoalPreviewResponse =
        GoalPreviewResponse.from(goalService.preview(guestUserId, monthlySavingManwon))

    @PostMapping("/goal")
    @ResponseStatus(HttpStatus.CREATED)
    override fun confirmGoal(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: GoalConfirmRequest,
    ): GoalResponse =
        GoalResponse.from(goalService.confirm(guestUserId, request.plan, request.monthlySavingManwon))
}
