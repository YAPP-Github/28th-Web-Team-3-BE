package backend.yapp.core.onboarding.service

/**
 * 목표 저축 미리보기(슬라이더). 매달 모을 금액을 고르면 예상 금액을 재계산한다.
 * 예상 금액 = 순자산([baseAmountManwon]) + 추가 저축액([additionalSavingManwon]). 모든 금액 단위는 만원.
 */
data class GoalPreview(
    val monthlySavingManwon: Int,            // 선택한 매달 모을 금액
    val currentMonthlySavingManwon: Int,     // 현재(온보딩 입력) 월 저축액 = 슬라이더 최소
    val minMonthlySavingManwon: Int,         // 슬라이더 최소(= 현재 저축액)
    val maxMonthlySavingManwon: Int,         // 슬라이더 최대
    val recommendedMonthlySavingManwon: Int, // 기본 위치(현재 + 권장 상향폭)
    val periodMonths: Int,
    val baseAmountManwon: Int,               // 순자산
    val additionalSavingManwon: Int,         // 매달 모을 금액 × 개월
    val expectedAmountManwon: Int,           // 순자산 + 추가 저축액
    val extraMonthlyManwon: Int,             // 현재 대비 매달 더 모으는 금액
    val extraPercent: Int,                   // 현재 대비 상향 비율(%)
)
