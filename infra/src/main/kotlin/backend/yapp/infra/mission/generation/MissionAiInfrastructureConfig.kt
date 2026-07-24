package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(MissionGenerationProperties::class)
class MissionAiInfrastructureConfig {
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
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["provider"],
        havingValue = "openai",
    )
    fun openAiMissionDraftContentGenerator(
        chatClientBuilder: ChatClient.Builder,
        objectMapper: ObjectMapper,
        properties: MissionGenerationProperties,
        @Value("\${spring.ai.openai.api-key:}") apiKey: String,
    ): MissionDraftContentGenerator {
        require(apiKey.isNotBlank()) {
            "OPENAI_API_KEY is required when mission.generation.provider=openai"
        }
        return SpringAiMissionDraftContentGenerator(
            client = ChatClientMissionDraftAiClient(chatClientBuilder.build()),
            objectMapper = objectMapper,
            prompt = properties.prompt,
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation.recommendation",
        name = ["semantic-provider"],
        havingValue = "keyword",
        matchIfMissing = true,
    )
    fun keywordMissionSemanticRetriever(): MissionSemanticRetriever = KeywordMissionSemanticRetriever()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation.recommendation",
        name = ["semantic-provider"],
        havingValue = "openai",
    )
    fun openAiMissionSemanticRetriever(
        embeddingModel: EmbeddingModel,
        properties: MissionGenerationProperties,
        @Value("\${spring.ai.openai.api-key:}") apiKey: String,
    ): MissionSemanticRetriever {
        require(apiKey.isNotBlank()) {
            "OPENAI_API_KEY is required when mission semantic provider is openai"
        }
        return FallbackMissionSemanticRetriever(
            primary = SpringAiMissionSemanticRetriever(
                client = SpringAiMissionEmbeddingClient(embeddingModel),
                modelVersion = properties.recommendation.embedding.modelVersion,
            ),
            fallback = KeywordMissionSemanticRetriever(),
        )
    }
}
