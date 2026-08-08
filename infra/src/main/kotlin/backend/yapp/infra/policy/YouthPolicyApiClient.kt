package backend.yapp.infra.policy

import backend.yapp.core.policy.port.ExternalYouthPolicy
import backend.yapp.core.policy.port.ExternalYouthPolicyPage
import backend.yapp.core.policy.port.YouthPolicyProviderPort
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

/**
 * 온통청년 청년정책 목록조회 API 어댑터. `GET {baseUrl}?apiKeyNm=...&pageType=1&rtnType=json&pageNum=&pageSize=`.
 * 응답 JSON 봉투(`result.youthPolicyList`, `result.pagging.totCount`)를 [ExternalYouthPolicy]로 변환한다.
 */
@Component
class YouthPolicyApiClient(
    private val properties: YouthPolicyProperties,
    private val objectMapper: ObjectMapper,
) : YouthPolicyProviderPort {
    private val restClient: RestClient = RestClient.builder().baseUrl(properties.baseUrl).build()

    override fun fetch(pageNum: Int, pageSize: Int): ExternalYouthPolicyPage {
        val json = restClient.get()
            .uri { builder ->
                builder
                    .queryParam("apiKeyNm", properties.apiKey)
                    .queryParam("pageType", "1")
                    .queryParam("rtnType", "json")
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", pageSize)
                    .build()
            }
            .retrieve()
            .body(String::class.java)
            ?: return ExternalYouthPolicyPage(emptyList(), 0)

        val response = objectMapper.readValue(json, YouthPolicyApiResponse::class.java)
        val items = response.result?.youthPolicyList ?: emptyList()
        return ExternalYouthPolicyPage(
            policies = items.mapNotNull { it.toExternal() },
            totalCount = response.result?.pagging?.totCount ?: items.size,
        )
    }
}

data class YouthPolicyApiResponse(val result: YouthPolicyApiResult? = null)

data class YouthPolicyApiResult(
    val youthPolicyList: List<YouthPolicyApiItem>? = null,
    val pagging: YouthPolicyApiPaging? = null,
)

data class YouthPolicyApiPaging(val totCount: Int? = null)

data class YouthPolicyApiItem(
    val plcyNo: String? = null,
    val plcyNm: String? = null,
    val plcyExplnCn: String? = null,
    val plcySprtCn: String? = null,
    val lclsfNm: String? = null,
    val mclsfNm: String? = null,
    val sprvsnInstCdNm: String? = null,
    val aplyUrlAddr: String? = null,
    val aplyYmd: String? = null,
    val bizPrdEndYmd: String? = null,
    val plcyAplyMthdCn: String? = null,
    val sbmsnDcmntCn: String? = null,
    val sprtTrgtMinAge: String? = null,
    val sprtTrgtMaxAge: String? = null,
    val earnEtcCn: String? = null,
    val addAplyQlfcCndCn: String? = null,
    val lastMdfcnDt: String? = null,
) {
    fun toExternal(): ExternalYouthPolicy? {
        val id = plcyNo.blankToNull() ?: return null
        val name = plcyNm.blankToNull() ?: return null
        return ExternalYouthPolicy(
            externalId = id,
            title = name,
            description = plcyExplnCn.blankToNull(),
            supportContent = plcySprtCn.blankToNull(),
            largeCategory = lclsfNm.blankToNull(),
            mediumCategory = mclsfNm.blankToNull(),
            supervisingOrg = sprvsnInstCdNm.blankToNull(),
            applyUrl = aplyUrlAddr.blankToNull(),
            applyPeriodText = aplyYmd.blankToNull(),
            bizEndYmd = bizPrdEndYmd.blankToNull(),
            applyMethod = plcyAplyMthdCn.blankToNull(),
            submitDocuments = sbmsnDcmntCn.blankToNull(),
            targetMinAge = sprtTrgtMinAge.blankToNull()?.toIntOrNull(),
            targetMaxAge = sprtTrgtMaxAge.blankToNull()?.toIntOrNull(),
            earnCondition = earnEtcCn.blankToNull(),
            additionalQualification = addAplyQlfcCndCn.blankToNull(),
            externalModifiedAt = lastMdfcnDt.blankToNull(),
        )
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.ifBlank { null }
