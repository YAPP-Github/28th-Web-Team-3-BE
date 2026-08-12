package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.web.client.RestClient
import org.springframework.http.client.SimpleClientHttpRequestFactory

@Configuration
@EnableConfigurationProperties(NaverBlogSearchProperties::class)
class NaverBlogInfrastructureConfig {
    @Bean
    fun missionBlogSearchPort(
        properties: NaverBlogSearchProperties,
        meterRegistry: MeterRegistry,
    ): MissionBlogSearchPort {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)
            setReadTimeout(5_000)
        }
        return NaverBlogSearchAdapter(
            RestClient.builder().requestFactory(requestFactory),
            properties,
            MicrometerNaverBlogSearchTelemetry(meterRegistry),
        )
    }
}
