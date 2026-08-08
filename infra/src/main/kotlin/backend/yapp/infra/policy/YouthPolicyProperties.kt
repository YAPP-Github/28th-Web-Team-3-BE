package backend.yapp.infra.policy

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 온통청년 청년정책 API 설정. 인증키는 환경변수(시크릿)로 주입한다.
 */
@ConfigurationProperties("youth-policy")
data class YouthPolicyProperties(
    val baseUrl: String = "https://www.youthcenter.go.kr/go/ythip/getPlcy",
    val apiKey: String = "",
    val pageSize: Int = 100,
)
