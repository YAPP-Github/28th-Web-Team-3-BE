package backend.yapp.infra.onboarding

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 온보딩 정책 값 외부 설정. 금리·저축률 상향폭·안별 uplift·세후→세전 보정계수를 코드 밖에서 관리한다.
 * (추후 Remote Config/DB로 승격 시 이 바인딩만 교체)
 */
@ConfigurationProperties("onboarding")
data class OnboardingProperties(
    val version: String,
    val annualRate: Double,
    val reportUpliftPercent: Int,
    val salaryCorrectionFactor: Double,
    val plan1: PlanUpliftProperties,
    val plan2: PlanUpliftProperties,
)

data class PlanUpliftProperties(
    val min: Double,
    val max: Double,
    val single: Double,
)
