package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeTracePort
import backend.yapp.core.mission.generation.port.MissionKnowledgeVerificationPort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(MissionGenerationProperties::class)
class MissionAiInfrastructureConfig {
    @Bean
    fun missionKnowledgeRetriever(
        jdbcTemplateProvider: ObjectProvider<JdbcTemplate>,
    ): MissionKnowledgeRetrievalPort = jdbcTemplateProvider.ifAvailable
        ?.let(::DatabaseMissionKnowledgeRetriever)
        ?: EmptyMissionKnowledgeRetriever()

    @Bean
    fun missionKnowledgeTraceRecorder(
        jdbcTemplateProvider: ObjectProvider<JdbcTemplate>,
    ): MissionKnowledgeTracePort = jdbcTemplateProvider.ifAvailable
        ?.let(::DatabaseMissionKnowledgeTraceRecorder)
        ?: NoopMissionKnowledgeTraceRecorder()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "off",
        matchIfMissing = true,
    )
    fun conservativeMissionKnowledgeVerifier(): MissionKnowledgeVerificationPort =
        ConservativeMissionKnowledgeVerifier()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "on",
    )
    fun officialSourceMissionKnowledgeVerifier(
        chatClientBuilder: ChatClient.Builder,
    ): MissionKnowledgeVerificationPort =
        OfficialSourceMissionKnowledgeVerifier(chatClientBuilder.build())

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "off",
        matchIfMissing = true,
    )
    fun staticMissionAlternativeGenerator(): MissionAlternativeGenerationPort =
        StaticMissionAlternativeGenerator()

    @Bean
    @ConditionalOnProperty(
        prefix = "mission.generation",
        name = ["ai-activation"],
        havingValue = "on",
    )
    fun aiMissionAlternativeGenerator(
        chatClientBuilder: ChatClient.Builder,
        @Value("\${spring.ai.google.genai.api-key:}") apiKey: String,
    ): MissionAlternativeGenerationPort {
        validateGoogleGenAiAuthentication(apiKey)
        return SpringAiMissionAlternativeGenerator(chatClientBuilder.build())
    }

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
        telemetry: MissionDraftGenerationTelemetry,
        @Value("\${spring.ai.google.genai.api-key:}") apiKey: String,
    ): MissionDraftContentGenerator {
        validateGoogleGenAiAuthentication(apiKey)
        return SpringAiMissionDraftContentGenerator(
            client = ChatClientMissionDraftAiClient(chatClientBuilder.build()),
            objectMapper = objectMapper,
            prompt = properties.prompt,
            telemetry = telemetry,
            rateLimitRetry = properties.rateLimitRetry,
        )
    }

    @Bean
    fun missionDraftGenerationTelemetry(
        meterRegistryProvider: ObjectProvider<MeterRegistry>,
    ): MissionDraftGenerationTelemetry =
        meterRegistryProvider.ifAvailable
            ?.let(::MicrometerMissionDraftGenerationTelemetry)
            ?: NoopMissionDraftGenerationTelemetry

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
    ): MissionSemanticRetriever {
        validateGoogleGenAiAuthentication(apiKey)
        return FallbackMissionSemanticRetriever(
            primary = SpringAiMissionSemanticRetriever(
                client = SpringAiMissionEmbeddingClient(embeddingModel),
                provider = GOOGLE_GENAI_PROVIDER,
                modelVersion = properties.recommendation.embedding.modelVersion,
            ),
            fallback = KeywordMissionSemanticRetriever(),
        )
    }

    private fun validateGoogleGenAiAuthentication(apiKey: String) {
        require(apiKey.isNotBlank()) {
            "AI_ACTIVATION=on requires GOOGLE_GENAI_API_KEY"
        }
    }

    companion object {
        private const val GOOGLE_GENAI_PROVIDER = "google-genai"
    }
}
