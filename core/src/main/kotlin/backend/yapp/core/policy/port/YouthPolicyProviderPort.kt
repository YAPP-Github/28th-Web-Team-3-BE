package backend.yapp.core.policy.port

/**
 * 온통청년 청년정책 API 추상화. 구현은 infra(WebClient)에서 제공한다.
 * 원본 필드를 정규화한 [ExternalYouthPolicy]로 반환하며, 카테고리·연령 등 값은 원본 문자열을 유지한다.
 */
interface YouthPolicyProviderPort {
    fun fetch(pageNum: Int, pageSize: Int): ExternalYouthPolicyPage
}

data class ExternalYouthPolicyPage(
    val policies: List<ExternalYouthPolicy>,
    val totalCount: Int,
)

data class ExternalYouthPolicy(
    val externalId: String,
    val title: String,
    val description: String? = null,
    val supportContent: String? = null,
    val largeCategory: String? = null,
    val mediumCategory: String? = null,
    val supervisingOrg: String? = null,
    val applyUrl: String? = null,
    val applyPeriodText: String? = null,
    val bizEndYmd: String? = null,
    val applyMethod: String? = null,
    val submitDocuments: String? = null,
    val targetMinAge: Int? = null,
    val targetMaxAge: Int? = null,
    val earnCondition: String? = null,
    val additionalQualification: String? = null,
    val externalModifiedAt: String? = null,
    /** 온통청년 지역코드(zipCd). 시군구 행정구역 코드 콤마 목록(앞 2자리=시도). */
    val regionCode: String? = null,
    /** 온통청년 조회수(inqCnt). 정렬 기준. */
    val viewCount: Int? = null,
    /** 온통청년 신청기간 구분코드(aplyPrdSeCd): 0057001 특정기간 / 0057002 상시 / 0057003 마감. */
    val applyPeriodType: String? = null,
)
