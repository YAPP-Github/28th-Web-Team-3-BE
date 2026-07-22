package backend.yapp.core.onboarding.calculator

/**
 * 자산·소득·소비 3축 2분법(8분기) 종합 분석 분기.
 * assetHigh: 순자산이 또래 중앙값 이상, incomeHigh: 소득 백분위 50 이상, consumptionHigh: 소비 백분위 50 이상(많이 씀).
 *
 * 진단·액션 문구는 스펙 명세의 분기 테이블을 그대로 옮긴 것이다. 액션의 `{N}`은 소비 절감 필요 금액으로 치환한다.
 */
enum class DiagnosisBranch(
    val code: Int,
    val assetHigh: Boolean,
    val incomeHigh: Boolean,
    val consumptionHigh: Boolean,
    val diagnosis: String,
    val action: String,
) {
    B1(1, true, true, true,
        "잘 벌고 잘 모았어요. 다만 소비도 또래보다 큰 편이에요",
        "지금 페이스만 유지해도 목표 달성이 빨라요"),
    B2(2, true, true, false,
        "소득·저축·자산 모두 또래 상위권이에요",
        "이제는 모으기보다 굴리는 쪽을 고민할 때예요"),
    B3(3, true, false, true,
        "모아둔 자산은 든든하지만 지금 현금 흐름이 빠듯해요",
        "고정비부터 점검하면 자산을 지킬 수 있어요"),
    B4(4, true, false, false,
        "소득이 넉넉하지 않은데도 알뜰하게 잘 모으셨어요",
        "저축 여력이 한계라 소득 늘리기를 함께 봐야 해요"),
    B5(5, false, true, true,
        "소득은 여력이 있는데 소비가 조금 높은 게 원인이에요",
        "소비를 월 {N}만원만 줄이면 저축률 10% 상향이 가능해요"),
    B6(6, false, true, false,
        "출발은 늦었지만 벌이도 저축 습관도 또래보다 좋아요",
        "자동이체로 저축을 고정하면 격차가 빠르게 좁혀져요"),
    B7(7, false, false, true,
        "지금은 모으는 속도보다 나가는 속도가 빨라요",
        "가장 큰 고정비 하나만 줄여도 흐름이 바뀌어요"),
    B8(8, false, false, false,
        "아낄 만큼 아끼고 계세요. 소비 문제는 아니에요",
        "소액이라도 자동저축부터 시작하는 게 효과적이에요"),
    ;

    companion object {
        fun of(assetHigh: Boolean, incomeHigh: Boolean, consumptionHigh: Boolean): DiagnosisBranch =
            entries.first {
                it.assetHigh == assetHigh &&
                    it.incomeHigh == incomeHigh &&
                    it.consumptionHigh == consumptionHigh
            }
    }
}
