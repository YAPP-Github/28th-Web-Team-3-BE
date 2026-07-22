package backend.yapp.infra.onboarding

import backend.yapp.core.onboarding.port.OnboardingConfig
import backend.yapp.core.onboarding.port.OnboardingConfigPort
import backend.yapp.core.onboarding.port.PlanUplift
import org.springframework.stereotype.Component

@Component
class OnboardingConfigAdapter(
    private val properties: OnboardingProperties,
) : OnboardingConfigPort {
    override fun current(): OnboardingConfig =
        OnboardingConfig(
            version = properties.version,
            annualRate = properties.annualRate,
            reportUpliftPercent = properties.reportUpliftPercent,
            salaryCorrectionFactor = properties.salaryCorrectionFactor,
            plan1 = properties.plan1.toPlanUplift(),
            plan2 = properties.plan2.toPlanUplift(),
        )

    private fun PlanUpliftProperties.toPlanUplift(): PlanUplift =
        PlanUplift(min = min, max = max, single = single)
}
