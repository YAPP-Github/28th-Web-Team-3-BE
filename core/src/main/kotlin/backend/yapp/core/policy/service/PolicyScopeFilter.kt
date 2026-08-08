package backend.yapp.core.policy.service

import backend.yapp.core.policy.port.ExternalYouthPolicy
import java.time.LocalDate

/**
 * 동기화 대상 청년정책을 가려내는 순수 필터.
 *
 * 규칙:
 * - 중분류가 포함 목록에 있으면 대상. 단 "취업" 중분류는 정책명에 "자격증"이 포함될 때만 대상.
 * - 신청/사업 종료일이 오늘보다 과거이면 제외(마감·사업종료). 종료일을 알 수 없으면 포함.
 * - 다중 카테고리(콤마)와 중복 표기는 정규화해 하나라도 포함되면 대상으로 본다.
 */
object PolicyScopeFilter {
    private val INCLUDED_MEDIUM = setOf(
        "건강",
        "교육비지원",
        "문화활동 및 생활지원",
        "취약계층 및 금융지원",
        "주택 및 거주지",
        "전월세 및 주거급여 지원",
        "문화활동",
        "기숙사",
        "재직자",
    )
    private const val EMPLOYMENT_MEDIUM = "취업"
    private const val CERTIFICATE_KEYWORD = "자격증"

    private val DATE_REGEX = Regex("""(\d{4})[-.]?(\d{2})[-.]?(\d{2})""")

    fun isInScope(policy: ExternalYouthPolicy, today: LocalDate): Boolean {
        if (!isIncludedCategory(policy)) return false
        val end = resolveEndDate(policy)
        return end == null || !end.isBefore(today)
    }

    private fun isIncludedCategory(policy: ExternalYouthPolicy): Boolean {
        val mediums = splitCategories(policy.mediumCategory)
        if (mediums.any { it in INCLUDED_MEDIUM }) return true
        return mediums.contains(EMPLOYMENT_MEDIUM) && policy.title.contains(CERTIFICATE_KEYWORD)
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
