package backend.yapp.infra.policy

import backend.yapp.core.policy.port.ExternalYouthPolicy
import backend.yapp.core.policy.port.ExternalYouthPolicyPage
import backend.yapp.core.policy.port.YouthPolicyProviderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

/**
 * 온통청년 청년정책 목록조회 API 어댑터. `GET {baseUrl}?apiKeyNm=...&pageType=1&rtnType=json&pageNum=&pageSize=`.
 * 응답 JSON 봉투(`result.youthPolicyList`, `result.pagging.totCount`)를 [ExternalYouthPolicy]로 변환한다.
 *
 * 온통청년 서버는 `User-Agent` 헤더가 없으면 500(HTML 에러 페이지)을 반환하므로 반드시 지정한다.
 * 정부 서버가 간헐적으로 5xx(HTML 에러 페이지)를 반환하는 경우가 있어 5xx/네트워크 오류는 짧게 재시도한다.
 */
@Component
class YouthPolicyApiClient(
    private val properties: YouthPolicyProperties,
    private val objectMapper: ObjectMapper,
) : YouthPolicyProviderPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .defaultHeader("User-Agent", USER_AGENT)
        .build()

    override fun fetch(pageNum: Int, pageSize: Int): ExternalYouthPolicyPage {
        val json = fetchWithRetry(pageNum, pageSize)
            ?: return ExternalYouthPolicyPage(emptyList(), 0)

        val response = objectMapper.readValue(json, YouthPolicyApiResponse::class.java)
        val items = response.result?.youthPolicyList ?: emptyList()
        return ExternalYouthPolicyPage(
            policies = items.mapNotNull { it.toExternal() },
            totalCount = response.result?.pagging?.totCount ?: items.size,
        )
    }

    private fun fetchWithRetry(pageNum: Int, pageSize: Int): String? {
        var lastError: RestClientException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return restClient.get()
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
            } catch (e: RestClientException) {
                lastError = e
                // 진단용: 상태/본문 스니펫만 남긴다(인증키는 URI에만 있고 로깅하지 않음).
                log.warn(
                    "온통청년 API 호출 실패 page={} attempt={}/{}: {}",
                    pageNum, attempt, MAX_ATTEMPTS, diagnose(e),
                )
                if (attempt < MAX_ATTEMPTS) Thread.sleep(RETRY_BACKOFF_MS * attempt)
            }
        }
        throw lastError!!
    }

    private fun diagnose(e: RestClientException): String {
        val server = e as? org.springframework.web.client.HttpStatusCodeException ?: return e.message.orEmpty()
        val body = server.responseBodyAsString.replace(Regex("\\s+"), " ").take(200)
        return "status=${server.statusCode} bodySnippet=[$body]"
    }

    companion object {
        private const val USER_AGENT = "YappBenefitSync/1.0"
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_BACKOFF_MS = 800L
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
    val zipCd: String? = null,
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
            regionCode = zipCd.blankToNull(),
        )
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.ifBlank { null }
