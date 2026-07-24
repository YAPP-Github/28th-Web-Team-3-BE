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
    fun `off uses template and keyword without AI model beans`() {
        contextRunner.run { context ->
            assertNull(context.startupFailure)
            assertIs<TemplateMissionDraftContentGenerator>(
                context.getBean(MissionDraftContentGenerator::class.java),
            )
            assertIs<KeywordMissionSemanticRetriever>(
                context.getBean(MissionSemanticRetriever::class.java),
            )
            assertNull(context.getBeanProvider(ChatClient.Builder::class.java).ifAvailable)
            assertNull(context.getBeanProvider(EmbeddingModel::class.java).ifAvailable)
        }
    }

    @Test
    fun `on fails fast without Google GenAI authentication`() {
        aiContextRunner()
            .withPropertyValues("mission.generation.ai-activation=on")
            .run { context ->
                assertNotNull(context.startupFailure)
            }
    }

    @Test
    fun `on fails fast with incomplete Vertex configuration`() {
        aiContextRunner()
            .withPropertyValues(
                "mission.generation.ai-activation=on",
                "spring.ai.google.genai.project-id=test-project",
            )
            .run { context ->
                assertNotNull(context.startupFailure)
            }
    }

    @Test
    fun `on creates both adapters with Gemini Developer API key`() {
        aiContextRunner()
            .withPropertyValues(
                "mission.generation.ai-activation=on",
                "spring.ai.google.genai.api-key=test-key",
            )
            .run { context ->
                assertNull(context.startupFailure)
                assertIs<SpringAiMissionDraftContentGenerator>(
                    context.getBean(MissionDraftContentGenerator::class.java),
                )
                assertIs<FallbackMissionSemanticRetriever>(
                    context.getBean(MissionSemanticRetriever::class.java),
                )
            }
    }

    @Test
    fun `on creates both adapters with Vertex project and location`() {
        aiContextRunner()
            .withPropertyValues(
                "mission.generation.ai-activation=on",
                "spring.ai.google.genai.project-id=test-project",
                "spring.ai.google.genai.location=asia-northeast3",
            )
            .run { context ->
                assertNull(context.startupFailure)
                assertIs<SpringAiMissionDraftContentGenerator>(
                    context.getBean(MissionDraftContentGenerator::class.java),
                )
                assertIs<FallbackMissionSemanticRetriever>(
                    context.getBean(MissionSemanticRetriever::class.java),
                )
            }
    }

    @Test
    fun `API key takes precedence over incomplete Vertex values`() {
        aiContextRunner()
            .withPropertyValues(
                "mission.generation.ai-activation=on",
                "spring.ai.google.genai.api-key=test-key",
                "spring.ai.google.genai.project-id=ignored-project",
            )
            .run { context ->
                assertNull(context.startupFailure)
                assertIs<SpringAiMissionDraftContentGenerator>(
                    context.getBean(MissionDraftContentGenerator::class.java),
                )
            }
    }

    private fun aiContextRunner(): ApplicationContextRunner =
        contextRunner
            .withBean(ChatClient.Builder::class.java, {
                ChatClient.builder(Mockito.mock(ChatModel::class.java))
            })
            .withBean(EmbeddingModel::class.java, {
                Mockito.mock(EmbeddingModel::class.java)
            })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
}
