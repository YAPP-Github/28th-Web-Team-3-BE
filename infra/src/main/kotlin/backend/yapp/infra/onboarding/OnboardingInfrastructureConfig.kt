package backend.yapp.infra.onboarding

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OnboardingProperties::class)
class OnboardingInfrastructureConfig
