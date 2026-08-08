package backend.yapp.core.policy.domain

/**
 * 혜택(청년정책) 노출용 4분류. 온통청년 대분류가 구/신 체계 혼재·다중값으로 지저분해,
 * 중분류를 이 4개로 정규화해 필터·노출에 사용한다.
 *
 * 선언 순서 = 다중 중분류일 때 대표 카테고리 선정 우선순위(주거 > 금융 > 교육 > 복지).
 */
enum class PolicyCategory(val label: String) {
    HOUSING("주거"),
    FINANCE("금융"),
    EDUCATION("교육"),
    WELFARE("복지"),
    ;

    companion object {
        fun fromLabel(label: String?): PolicyCategory? = entries.firstOrNull { it.label == label }
    }
}
