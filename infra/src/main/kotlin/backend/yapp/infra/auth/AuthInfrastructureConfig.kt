package backend.yapp.infra.auth

import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class AuthInfrastructureConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
