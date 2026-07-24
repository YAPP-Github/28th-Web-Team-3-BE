package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class MissionAiInfrastructureConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(MissionAiInfrastructureConfig::class.java)

    @Test
    fun `starts without API key with mock and keyword defaults`() {
        contextRunner.run { context ->
            assertNull(context.startupFailure)
            assertIs<TemplateMissionDraftContentGenerator>(
                context.getBean(MissionDraftContentGenerator::class.java),
            )
            assertIs<KeywordMissionSemanticRetriever>(
                context.getBean(MissionSemanticRetriever::class.java),
            )
        }
    }

    @Test
    fun `fails fast when openai content provider has no API key`() {
        contextRunner
            .withPropertyValues("mission.generation.provider=openai")
            .withBean(ChatClient.Builder::class.java, {
                ChatClient.builder(Mockito.mock(ChatModel::class.java))
            })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertNotNull(context.startupFailure)
            }
    }

    @Test
    fun `fails fast when openai semantic provider has no API key`() {
        contextRunner
            .withPropertyValues("mission.generation.recommendation.semantic-provider=openai")
            .withBean(EmbeddingModel::class.java, {
                Mockito.mock(EmbeddingModel::class.java)
            })
            .run { context ->
                assertNotNull(context.startupFailure)
            }
    }

    @Test
    fun `creates Spring AI content adapter when openai key and model are configured`() {
        contextRunner
            .withPropertyValues(
                "mission.generation.provider=openai",
                "spring.ai.openai.api-key=test-key",
            )
            .withBean(ChatClient.Builder::class.java, {
                ChatClient.builder(Mockito.mock(ChatModel::class.java))
            })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertIs<SpringAiMissionDraftContentGenerator>(
                    context.getBean(MissionDraftContentGenerator::class.java),
                )
            }
    }

    @Test
    fun `creates fallback semantic adapter when openai key and embedding model are configured`() {
        contextRunner
            .withPropertyValues(
                "mission.generation.recommendation.semantic-provider=openai",
                "spring.ai.openai.api-key=test-key",
            )
            .withBean(EmbeddingModel::class.java, {
                Mockito.mock(EmbeddingModel::class.java)
            })
            .run { context ->
                assertIs<FallbackMissionSemanticRetriever>(
                    context.getBean(MissionSemanticRetriever::class.java),
                )
            }
    }
}
