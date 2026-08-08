package backend.yapp.infra.policy

import backend.yapp.core.policy.port.ExternalYouthPolicy
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 사람이 온통청년 API에서 직접 받아온 응답 JSON을 [ExternalYouthPolicy] 목록으로 파싱한다.
 * 어댑터([YouthPolicyApiClient])와 동일한 응답 봉투(`result.youthPolicyList`) 매핑을 재사용한다.
 */
@Component
class YouthPolicyResponseParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(json: String): List<ExternalYouthPolicy> {
        val response = objectMapper.readValue(json, YouthPolicyApiResponse::class.java)
        return response.result?.youthPolicyList?.mapNotNull { it.toExternal() } ?: emptyList()
    }
}
