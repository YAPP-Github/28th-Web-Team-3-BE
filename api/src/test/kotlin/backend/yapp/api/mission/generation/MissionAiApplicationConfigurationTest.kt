package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionSemanticRetriever
import backend.yapp.infra.mission.generation.FallbackMissionSemanticRetriever
import backend.yapp.infra.mission.generation.MissionAiInfrastructureConfig
import backend.yapp.infra.mission.generation.SpringAiMissionDraftContentGenerator
import backend.yapp.infra.mission.generation.TemplateMissionDraftContentGenerator
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

class MissionAiApplicationConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                autoConfiguration(
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
                ),
                autoConfiguration(
                    "org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration",
                ),
            ),
        )
        .withUserConfiguration(
            MissionAiInfrastructureConfig::class.java,
            ObjectMapperTestConfig::class.java,
        )
        .withPropertyValues("SPRING_PROFILES_ACTIVE=test")

    @Test
    fun `application yml keeps AI models disabled without a key`() {
        contextRunner.run { context ->
            assertEquals(null, context.startupFailure)
            assertIs<TemplateMissionDraftContentGenerator>(
                context.getBean(MissionDraftContentGenerator::class.java),
            )
            assertEquals("mock", context.environment.getProperty("spring.ai.model.chat"))
            assertEquals("keyword", context.environment.getProperty("spring.ai.model.embedding"))
            assertEquals("PT20S", context.environment.getProperty("spring.ai.openai.chat.timeout"))
            assertEquals("1", context.environment.getProperty("spring.ai.openai.chat.max-retries"))
        }
    }

    @Test
    fun `configured properties create the real Spring AI chat adapter with bounded options`() {
        contextRunner
            .withPropertyValues(
                "mission.generation.provider=openai",
                "spring.ai.model.chat=openai",
                "spring.ai.openai.api-key=test-key",
            )
            .run { context ->
                assertEquals(null, context.startupFailure)
                assertIs<SpringAiMissionDraftContentGenerator>(
                    context.getBean(MissionDraftContentGenerator::class.java),
                )
                val properties = context.getBean(
                    autoConfiguration(
                        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties",
                    ),
                )
                assertEquals(Duration.ofSeconds(20), property(properties, "getTimeout"))
                assertEquals(1, property(properties, "getMaxRetries"))
                assertEquals(2_000, property(properties, "getMaxCompletionTokens"))
            }
    }

    @Test
    fun `configured properties create the real Spring AI embedding adapter with bounded options`() {
        contextRunner
            .withPropertyValues(
                "mission.generation.recommendation.semantic-provider=openai",
                "spring.ai.model.embedding=openai",
                "spring.ai.openai.api-key=test-key",
            )
            .run { context ->
                assertEquals(null, context.startupFailure)
                assertIs<FallbackMissionSemanticRetriever>(
                    context.getBean(MissionSemanticRetriever::class.java),
                )
                val properties = context.getBean(
                    autoConfiguration(
                        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties",
                    ),
                )
                assertEquals(Duration.ofSeconds(10), property(properties, "getTimeout"))
                assertEquals(1, property(properties, "getMaxRetries"))
            }
    }

    private fun autoConfiguration(className: String): Class<*> = Class.forName(className)

    private fun property(bean: Any, getter: String): Any? = bean.javaClass.getMethod(getter).invoke(bean)

    @Configuration(proxyBeanMethods = false)
    class ObjectMapperTestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }
}
