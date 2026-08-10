package backend.yapp.core.policy.service

import backend.yapp.core.onboarding.domain.ResidentialArea
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

    /**
     * 온통청년 지역코드(zipCd) 앞 2자리(시도) → 거주지역([ResidentialArea]).
     * 2026 행정개편 데이터 기준(12=전남광주통합특별시). 전국 정책은 16개 코드를 모두 포함한다.
     */
    private val SIDO_CODE_TO_AREA: Map<String, ResidentialArea> = mapOf(
        "11" to ResidentialArea.SEOUL,
        "12" to ResidentialArea.JEONNAM,
        "26" to ResidentialArea.BUSAN,
        "27" to ResidentialArea.DAEGU,
        "28" to ResidentialArea.INCHEON,
        "30" to ResidentialArea.DAEJEON,
        "31" to ResidentialArea.ULSAN,
        "36" to ResidentialArea.SEJONG,
        "41" to ResidentialArea.GYEONGGI,
        "43" to ResidentialArea.CHUNGBUK,
        "44" to ResidentialArea.CHUNGNAM,
        "47" to ResidentialArea.GYEONGBUK,
        "48" to ResidentialArea.GYEONGNAM,
        "50" to ResidentialArea.JEJU,
        "51" to ResidentialArea.GANGWON,
        "52" to ResidentialArea.JEONBUK,
    )

    /** 저장/조회 지역 필터용 구분자 포함 문자열(예: `,SEOUL,BUSAN,`). LIKE 매칭을 안전하게 하기 위해 앞뒤에 콤마를 둔다. */
    const val REGION_DELIMITER = ","

    /**
     * 정책의 지역코드(zipCd)를 거주지역 집합으로 정규화해 `,SEOUL,JEONNAM,` 형태 문자열로 만든다.
     * 지역코드가 없거나 알려진 시도가 없으면 null.
     */
    fun resolveRegionCodes(policy: ExternalYouthPolicy): String? {
        val areas = policy.regionCode
            ?.split(",")
            ?.mapNotNull { code -> SIDO_CODE_TO_AREA[code.trim().take(2)] }
            ?.toSortedSet()
            ?: return null
        if (areas.isEmpty()) return null
        return areas.joinToString(separator = REGION_DELIMITER, prefix = REGION_DELIMITER, postfix = REGION_DELIMITER) { it.name }
    }

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
