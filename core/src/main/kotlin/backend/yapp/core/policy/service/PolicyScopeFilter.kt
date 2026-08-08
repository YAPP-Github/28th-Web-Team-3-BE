package backend.yapp.core.policy.service

import backend.yapp.core.policy.domain.PolicyCategory
import backend.yapp.core.policy.port.ExternalYouthPolicy
import java.time.LocalDate

/**
 * 동기화 대상 청년정책을 가려내고 노출용 4분류([PolicyCategory])로 정규화하는 순수 필터.
 *
 * 규칙:
 * - 중분류가 매핑표에 있으면 대상이며 해당 4분류로 분류. 단 "취업" 중분류는 정책명에 "자격증"이 포함될 때만 대상(→교육).
 * - 신청/사업 종료일이 오늘보다 과거이면 제외(마감·사업종료). 종료일을 알 수 없으면 포함.
 * - 다중 카테고리(콤마)와 중복 표기는 정규화한다. 여러 4분류에 걸치면 우선순위(주거>금융>교육>복지)로 대표 1개.
 */
object PolicyScopeFilter {
    private val MEDIUM_TO_CATEGORY: Map<String, PolicyCategory> = mapOf(
        "주택 및 거주지" to PolicyCategory.HOUSING,
        "전월세 및 주거급여 지원" to PolicyCategory.HOUSING,
        "기숙사" to PolicyCategory.HOUSING,
        "취약계층 및 금융지원" to PolicyCategory.FINANCE,
        "재직자" to PolicyCategory.FINANCE,
        "교육비지원" to PolicyCategory.EDUCATION,
        "건강" to PolicyCategory.WELFARE,
        "문화활동 및 생활지원" to PolicyCategory.WELFARE,
        "문화활동" to PolicyCategory.WELFARE,
    )
    private const val EMPLOYMENT_MEDIUM = "취업"
    private const val CERTIFICATE_KEYWORD = "자격증"

    private val DATE_REGEX = Regex("""(\d{4})[-.]?(\d{2})[-.]?(\d{2})""")

    fun isInScope(policy: ExternalYouthPolicy, today: LocalDate): Boolean {
        if (resolveCategory(policy) == null) return false
        val end = resolveEndDate(policy)
        return end == null || !end.isBefore(today)
    }

    /** 중분류를 4분류로 정규화한다. 어느 분류에도 안 걸리면 null(=스코프 밖). 다중이면 우선순위(선언 순서)로 대표 1개. */
    fun resolveCategory(policy: ExternalYouthPolicy): PolicyCategory? {
        val mediums = splitCategories(policy.mediumCategory)
        val categories = mediums.mapNotNullTo(mutableSetOf()) { MEDIUM_TO_CATEGORY[it] }
        if (mediums.contains(EMPLOYMENT_MEDIUM) && policy.title.contains(CERTIFICATE_KEYWORD)) {
            categories.add(PolicyCategory.EDUCATION)
        }
        return categories.minByOrNull { it.ordinal }
    }

    /** 다중/중복 카테고리 정규화: 콤마 분리 → 공백 제거 → 중복 제거. */
    fun splitCategories(raw: String?): Set<String> =
        raw?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /** 신청기간·사업기간 문자열에서 가장 늦은 날짜를 종료일로 본다. 파싱 불가하면 null(기간 미상 → 포함). */
    fun resolveEndDate(policy: ExternalYouthPolicy): LocalDate? {
        val candidates = listOfNotNull(policy.applyPeriodText, policy.bizEndYmd).joinToString(" ")
        return DATE_REGEX.findAll(candidates)
            .mapNotNull { runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull() }
            .maxOrNull()
    }
}
