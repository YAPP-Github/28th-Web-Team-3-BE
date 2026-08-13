package backend.yapp.api.goal.controller

import backend.yapp.api.goal.dto.GoalV2Response
import backend.yapp.core.goal.service.GoalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Goal v2", description = "목표 상세 조회 v2. 기존 현황에 월별 저축 현황(막대그래프)이 추가된다. 저축 입력·목표 수정은 기존 /api/goal 사용.")
@SecurityRequirement(name = "accessTokenAuth")
@RestController
@RequestMapping("/api/v2/goal")
class GoalV2Controller(
    private val goalService: GoalService,
) {
    @Operation(
        summary = "목표 상세 조회 (v2)",
        description = "기존 목표 현황(목표액·진행률·서비스 사용기간·이번 달 목표 등)에 더해 " +
            "월별 저축 현황(`monthlySavings`: 목표 시작월부터 이번 달까지 각 달의 저축액, 오름차순)을 반환한다. " +
            "미입력 달은 0, 이번 달 항목은 `current=true`. 목표가 없으면 409(GOAL_ONBOARDING_REQUIRED).",
    )
    @GetMapping
    fun get(@AuthenticationPrincipal guestUserId: Long): GoalV2Response =
        GoalV2Response.from(goalService.statusV2(guestUserId))
}
