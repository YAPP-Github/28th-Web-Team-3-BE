package backend.yapp.infra.policy

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(YouthPolicyProperties::class)
class PolicyInfrastructureConfig
