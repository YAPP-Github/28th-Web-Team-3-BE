package backend.yapp.api.goal.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 현재 저축액 입력. 입력 금액이 이번 달 저축액과 총 저축액에 누적된다. */
data class SavingRequest(
    @field:Schema(description = "이번에 저축한 금액(만원). 총 저축액·이번 달 저축액에 누적된다.", example = "30")
    @field:Min(1) @field:Max(100_000)
    val amountManwon: Int,
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
