package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import java.util.concurrent.ThreadPoolExecutor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(MissionGenerationProperties::class)
class MissionGenerationInfrastructureConfig {
    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["provider"],
        havingValue = "mock",
        matchIfMissing = true,
    )
    fun templateMissionDraftContentGenerator(): MissionDraftContentGenerator =
        TemplateMissionDraftContentGenerator()

    @Bean
    @ConditionalOnProperty(prefix = "mission.generation", name = ["provider"], havingValue = "openai")
    fun openAiMissionDraftContentGenerator(
        objectMapper: ObjectMapper,
        properties: MissionGenerationProperties,
    ): MissionDraftContentGenerator {
        val client = JdkOpenAiResponsesClient(properties.openai)
        return OpenAiMissionDraftContentGenerator(client, objectMapper, properties.openai)
    }

    @Bean("missionGenerationTaskExecutor")
    fun missionGenerationTaskExecutor(properties: MissionGenerationProperties): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.executor.corePoolSize
            maxPoolSize = properties.executor.maxPoolSize
            queueCapacity = properties.executor.queueCapacity
            setThreadNamePrefix("mission-generation-")
            setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
            initialize()
        }
}
