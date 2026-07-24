package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.infra.mission.generation.FallbackMissionSemanticRetriever
import backend.yapp.infra.mission.generation.KeywordMissionSemanticRetriever
import backend.yapp.infra.mission.generation.MissionAiInfrastructureConfig
import backend.yapp.infra.mission.generation.SpringAiMissionDraftContentGenerator
import backend.yapp.infra.mission.generation.TemplateMissionDraftContentGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

class MissionAiApplicationConfigurationTest {
    @Test
    fun `off loads spring factories switch and prevents Google model beans despite conflicts`() {
        runApplication(
            "AI_ACTIVATION=off",
            "GOOGLE_GENAI_API_KEY=test-key",
            "spring.ai.model.chat=google-genai",
            "spring.ai.model.embedding.text=google-genai",
        ) { context ->
            assertEquals("off", context.environment.getProperty("mission.generation.ai-activation"))
            assertEquals("none", context.environment.getProperty("spring.ai.model.chat"))
            assertEquals("none", context.environment.getProperty("spring.ai.model.embedding.text"))
            assertIs<TemplateMissionDraftContentGenerator>(
                context.getBean(MissionDraftContentGenerator::class.java),
            )
            assertIs<KeywordMissionSemanticRetriever>(
                context.getBean(MissionSemanticRetriever::class.java),
            )
            assertNull(context.getBeanProvider(modelClass(CHAT_MODEL_CLASS)).ifAvailable)
            assertNull(context.getBeanProvider(modelClass(EMBEDDING_MODEL_CLASS)).ifAvailable)
        }
    }

    @Test
    fun `on creates real Google GenAI chat and embedding adapters`() {
        runApplication(
            "AI_ACTIVATION=on",
            "GOOGLE_GENAI_API_KEY=test-key",
            "spring.ai.model.chat=none",
            "spring.ai.model.embedding.text=none",
        ) { context ->
            assertEquals("on", context.environment.getProperty("mission.generation.ai-activation"))
            assertEquals("google-genai", context.environment.getProperty("spring.ai.model.chat"))
            assertEquals("google-genai", context.environment.getProperty("spring.ai.model.embedding.text"))
            assertIs<SpringAiMissionDraftContentGenerator>(
                context.getBean(MissionDraftContentGenerator::class.java),
            )
            assertIs<FallbackMissionSemanticRetriever>(
                context.getBean(MissionSemanticRetriever::class.java),
            )
            assertNotNull(context.getBean(modelClass(CHAT_MODEL_CLASS)))
            assertNotNull(context.getBean(modelClass(EMBEDDING_MODEL_CLASS)))
            assertEquals(
                "gemini-3.1-flash-lite",
                context.environment.getProperty("spring.ai.google.genai.chat.model"),
            )
            assertEquals(
                "text-embedding-004",
                context.environment.getProperty("spring.ai.google.genai.embedding.text.model"),
            )
        }
    }

    @Test
    fun `on fails without Developer API key or Vertex configuration`() {
        assertFails {
            runApplication("AI_ACTIVATION=on") { }
        }
    }

    @Test
    fun `invalid activation fails before application context starts`() {
        assertFails {
            runApplication("AI_ACTIVATION=ON") { }
        }
    }

    private fun runApplication(
        vararg properties: String,
        assertions: (ConfigurableApplicationContext) -> Unit,
    ) {
        TestPropertyValues.of(
            "SPRING_PROFILES_ACTIVE=test",
            *properties,
        ).applyToSystemProperties {
            val application = SpringApplication(
                MissionAiInfrastructureConfig::class.java,
                ObjectMapperTestConfig::class.java,
                autoConfiguration(
                    "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration",
                ),
            ).apply {
                setWebApplicationType(WebApplicationType.NONE)
            }
            application.run().use(assertions)
        }
    }

    private fun autoConfiguration(className: String): Class<*> = Class.forName(className)

    @Suppress("UNCHECKED_CAST")
    private fun modelClass(className: String): Class<Any> = Class.forName(className) as Class<Any>

    @Configuration(proxyBeanMethods = false)
    class ObjectMapperTestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }

    companion object {
        private const val CHAT_MODEL_CLASS = "org.springframework.ai.chat.model.ChatModel"
        private const val EMBEDDING_MODEL_CLASS = "org.springframework.ai.embedding.EmbeddingModel"
    }
}
