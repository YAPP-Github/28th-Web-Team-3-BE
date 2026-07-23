package backend.yapp.api.goal.controller

import backend.yapp.api.goal.dto.GoalStatusResponse
import backend.yapp.api.goal.dto.GoalUpdateRequest
import backend.yapp.api.goal.dto.SavingRequest
import backend.yapp.apidoc.goal.GoalApi
import backend.yapp.core.goal.service.GoalService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/goal")
class GoalController(
    private val goalService: GoalService,
) : GoalApi {
    @GetMapping
    override fun get(@AuthenticationPrincipal guestUserId: Long): GoalStatusResponse =
        GoalStatusResponse.from(goalService.status(guestUserId))

    @PostMapping("/savings")
    override fun addSaving(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: SavingRequest,
    ): GoalStatusResponse =
        GoalStatusResponse.from(goalService.addSaving(guestUserId, request.amountManwon))

    @PatchMapping
    override fun update(
        @AuthenticationPrincipal guestUserId: Long,
        @Valid @RequestBody request: GoalUpdateRequest,
    ): GoalStatusResponse =
        GoalStatusResponse.from(goalService.updateGoal(guestUserId, request.targetAmountManwon, request.periodMonths))
}
