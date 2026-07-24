package backend.yapp.api.goal.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 현재 저축액 입력. 이번 달 저축액을 입력값으로 덮어쓴다(set). */
data class SavingRequest(
    @field:Schema(description = "이번 달 저축액(만원). 이 값으로 이번 달 저축액을 덮어쓰며, 총 저축액에 반영된다.", example = "30")
    @field:Min(0) @field:Max(100_000)
    val savedAmountManwon: Int,
)

/** 목표 금액/기간 수정. 변경할 필드만 전송한다. */
data class GoalUpdateRequest(
    @field:Schema(description = "전체 목표 금액(만원)", example = "5000")
    @field:Min(1) @field:Max(1_000_000)
    val targetAmountManwon: Int? = null,
    @field:Schema(description = "목표 기간(개월). 3~36", example = "24")
    @field:Min(3) @field:Max(36)
    val periodMonths: Int? = null,
)
