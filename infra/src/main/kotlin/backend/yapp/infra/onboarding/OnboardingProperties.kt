package backend.yapp.infra.onboarding

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 온보딩 정책 값 외부 설정. 금리·저축률 상향폭·안별 uplift·세후→세전 보정계수를 코드 밖에서 관리한다.
 * 잘못된 설정은 애플리케이션 시작 시점에 검증으로 걸러진다.
 * (추후 Remote Config/DB로 승격 시 이 바인딩만 교체)
 */
@Validated
@ConfigurationProperties("onboarding")
data class OnboardingProperties(
    @field:NotBlank
    val version: String,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0")
    val annualRate: Double,
    @field:Min(0) @field:Max(100)
    val reportUpliftPercent: Int,
    @field:DecimalMin("1.0")
    val salaryCorrectionFactor: Double,
    @field:Valid
    val plan1: PlanUpliftProperties,
    @field:Valid
    val plan2: PlanUpliftProperties,
)

data class PlanUpliftProperties(
    @field:DecimalMin("0.0") @field:DecimalMax("1.0")
    val min: Double,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0")
    val max: Double,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0")
    val single: Double,
) {
    @get:AssertTrue(message = "uplift 값은 min <= single <= max 를 만족해야 합니다.")
    val validRange: Boolean
        get() = min <= single && single <= max
}
