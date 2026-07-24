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
        name = ["ai-activation"],
        havingValue = "off",
        matchIfMissing = true,
    )
    fun templateMissionDraftContentGenerator(): MissionDraftContentGenerator =
        TemplateMissionDraftContentGenerator()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "on",
    )
    fun aiMissionDraftContentGenerator(
        chatClientBuilder: ChatClient.Builder,
        objectMapper: ObjectMapper,
        properties: MissionGenerationProperties,
        @Value("\${spring.ai.google.genai.api-key:}") apiKey: String,
        @Value("\${spring.ai.google.genai.project-id:}") projectId: String,
        @Value("\${spring.ai.google.genai.location:}") location: String,
    ): MissionDraftContentGenerator {
        validateGoogleGenAiAuthentication(apiKey, projectId, location)
        return SpringAiMissionDraftContentGenerator(
            client = ChatClientMissionDraftAiClient(chatClientBuilder.build()),
            objectMapper = objectMapper,
            prompt = properties.prompt,
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "off",
        matchIfMissing = true,
    )
    fun keywordMissionSemanticRetriever(): MissionSemanticRetriever = KeywordMissionSemanticRetriever()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "on",
    )
    fun aiMissionSemanticRetriever(
        embeddingModel: EmbeddingModel,
        properties: MissionGenerationProperties,
        @Value("\${spring.ai.google.genai.api-key:}") apiKey: String,
        @Value("\${spring.ai.google.genai.project-id:}") projectId: String,
        @Value("\${spring.ai.google.genai.location:}") location: String,
    ): MissionSemanticRetriever {
        validateGoogleGenAiAuthentication(apiKey, projectId, location)
        return FallbackMissionSemanticRetriever(
            primary = SpringAiMissionSemanticRetriever(
                client = SpringAiMissionEmbeddingClient(embeddingModel),
                provider = GOOGLE_GENAI_PROVIDER,
                modelVersion = properties.recommendation.embedding.modelVersion,
            ),
            fallback = KeywordMissionSemanticRetriever(),
        )
    }

    private fun validateGoogleGenAiAuthentication(
        apiKey: String,
        projectId: String,
        location: String,
    ) {
        if (apiKey.isNotBlank()) {
            return
        }
        require(projectId.isNotBlank() && location.isNotBlank()) {
            "AI_ACTIVATION=on requires GOOGLE_GENAI_API_KEY or both GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION"
        }
    }

    companion object {
        private const val GOOGLE_GENAI_PROVIDER = "google-genai"
    }
}
