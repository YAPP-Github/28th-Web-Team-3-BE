package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(NaverBlogSearchProperties::class)
class NaverBlogInfrastructureConfig {
    @Bean
    fun missionBlogSearchPort(
        properties: NaverBlogSearchProperties,
        meterRegistry: MeterRegistry,
        objectMapper: ObjectMapper,
    ): MissionBlogSearchPort {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)
            setReadTimeout(5_000)
        }
        return NaverBlogSearchAdapter(
            RestClient.builder().requestFactory(requestFactory),
            properties,
            objectMapper,
            MicrometerNaverBlogSearchTelemetry(meterRegistry),
        )
    }
}
