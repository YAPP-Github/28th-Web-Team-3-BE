package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.infra.mission.generation.FallbackMissionSemanticRetriever
import backend.yapp.infra.mission.generation.KeywordMissionSemanticRetriever
import backend.yapp.infra.mission.generation.MissionAiInfrastructureConfig
import backend.yapp.infra.mission.generation.MissionDraftGenerationTelemetry
import backend.yapp.infra.mission.generation.MicrometerMissionDraftGenerationTelemetry
import backend.yapp.infra.mission.generation.SpringAiMissionDraftContentGenerator
import backend.yapp.infra.mission.generation.TemplateMissionDraftContentGenerator
import com.google.genai.Client
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

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
            assertEquals(
                GOOGLE_GENAI_EMBEDDING_CONNECTION_AUTO_CONFIGURATION,
                context.environment.getProperty(AUTO_CONFIGURATION_EXCLUDE_PROPERTY),
            )
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
    fun `off starts discovered auto configuration without Google credentials`() {
        runAutoConfiguredApplication(
            "AI_ACTIVATION=off",
            "GOOGLE_GENAI_API_KEY=",
            "spring.ai.google.genai.project-id=",
        ) { context ->
            assertEquals("off", context.environment.getProperty("mission.generation.ai-activation"))
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
    fun `auto configured application provides Micrometer telemetry`() {
        runAutoConfiguredApplication(
            "AI_ACTIVATION=off",
            "GOOGLE_GENAI_API_KEY=",
        ) { context ->
            assertIs<MicrometerMissionDraftGenerationTelemetry>(
                context.getBean(MissionDraftGenerationTelemetry::class.java),
            )
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
            val client = context.getBean(modelClass(GEN_AI_CLIENT_CLASS))
            val chatModel = context.getBean(modelClass(CHAT_MODEL_CLASS))
            val clientField = chatModel.javaClass.getDeclaredField("genAiClient").apply { trySetAccessible() }
            assertEquals(client, clientField.get(chatModel))
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
    fun `active alternative generation path uses the configured provider timeout`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody("{}"))
            Client.setDefaultBaseUrls(Optional.of(server.url("/").toString()), Optional.empty())
            try {
                runApplication(
                    "AI_ACTIVATION=on",
                    "GOOGLE_GENAI_API_KEY=test-key",
                    "mission.generation.provider-timeout=PT0.2S",
                ) { context ->
                    val generator = context.getBean(MissionAlternativeGenerationPort::class.java)
                    val startedAt = System.nanoTime()
                    assertFails {
                        generator.generate(MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()))
                    }
                    assertTrue(Duration.ofNanos(System.nanoTime() - startedAt) < Duration.ofMillis(800))
                }
            } finally {
                Client.setDefaultBaseUrls(Optional.empty(), Optional.empty())
            }
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

    private fun runAutoConfiguredApplication(
        vararg properties: String,
        assertions: (ConfigurableApplicationContext) -> Unit,
    ) {
        TestPropertyValues.of(
            "SPRING_PROFILES_ACTIVE=test",
            *properties,
        ).applyToSystemProperties {
            SpringApplication(AutoConfiguredTestApplication::class.java)
                .apply {
                    setWebApplicationType(WebApplicationType.NONE)
                }
                .run()
                .use(assertions)
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(MissionAiInfrastructureConfig::class, ObjectMapperTestConfig::class)
    class AutoConfiguredTestApplication

    companion object {
        private const val CHAT_MODEL_CLASS = "org.springframework.ai.chat.model.ChatModel"
        private const val EMBEDDING_MODEL_CLASS = "org.springframework.ai.embedding.EmbeddingModel"
        private const val GEN_AI_CLIENT_CLASS = "com.google.genai.Client"
        private const val AUTO_CONFIGURATION_EXCLUDE_PROPERTY = "spring.autoconfigure.exclude"
        private const val GOOGLE_GENAI_EMBEDDING_CONNECTION_AUTO_CONFIGURATION =
            "org.springframework.ai.model.google.genai.autoconfigure.embedding." +
                "GoogleGenAiEmbeddingConnectionAutoConfiguration"
    }
}
