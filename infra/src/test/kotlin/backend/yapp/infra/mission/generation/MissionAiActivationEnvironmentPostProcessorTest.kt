package backend.yapp.infra.mission.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class MissionAiActivationEnvironmentPostProcessorTest {
    private val postProcessor = MissionAiActivationEnvironmentPostProcessor()

    @Test
    fun `off overrides conflicting provider model properties`() {
        val environment = MockEnvironment()
            .withProperty("AI_ACTIVATION", "off")
            .withProperty("spring.ai.model.chat", "google-genai")
            .withProperty("spring.ai.model.embedding.text", "google-genai")

        postProcessor.postProcessEnvironment(environment, SpringApplication())

        assertEquals("off", environment.getProperty("mission.generation.ai-activation"))
        assertEquals("none", environment.getProperty("spring.ai.model.chat"))
        assertEquals("none", environment.getProperty("spring.ai.model.embedding.text"))
        assertEquals(
            MissionAiActivationEnvironmentPostProcessor.GOOGLE_GENAI_EMBEDDING_CONNECTION_AUTO_CONFIGURATION,
            environment.getProperty(
                MissionAiActivationEnvironmentPostProcessor.AUTO_CONFIGURATION_EXCLUDE_PROPERTY,
            ),
        )
    }

    @Test
    fun `on overrides conflicting disabled model properties`() {
        val environment = MockEnvironment()
            .withProperty("AI_ACTIVATION", "on")
            .withProperty("spring.ai.model.chat", "none")
            .withProperty("spring.ai.model.embedding.text", "none")

        postProcessor.postProcessEnvironment(environment, SpringApplication())

        assertEquals("on", environment.getProperty("mission.generation.ai-activation"))
        assertEquals("google-genai", environment.getProperty("spring.ai.model.chat"))
        assertEquals("google-genai", environment.getProperty("spring.ai.model.embedding.text"))
        assertNull(
            environment.getProperty(
                MissionAiActivationEnvironmentPostProcessor.AUTO_CONFIGURATION_EXCLUDE_PROPERTY,
            ),
        )
    }

    @Test
    fun `missing activation defaults to off`() {
        val environment = MockEnvironment()

        postProcessor.postProcessEnvironment(environment, SpringApplication())

        assertEquals("off", environment.getProperty("mission.generation.ai-activation"))
        assertEquals("none", environment.getProperty("spring.ai.model.chat"))
        assertEquals("none", environment.getProperty("spring.ai.model.embedding.text"))
        assertEquals(
            MissionAiActivationEnvironmentPostProcessor.GOOGLE_GENAI_EMBEDDING_CONNECTION_AUTO_CONFIGURATION,
            environment.getProperty(
                MissionAiActivationEnvironmentPostProcessor.AUTO_CONFIGURATION_EXCLUDE_PROPERTY,
            ),
        )
    }

    @Test
    fun `off preserves normalized existing auto configuration exclusions idempotently`() {
        val existingExclusion = "com.example.FirstAutoConfiguration"
        val environment = MockEnvironment()
            .withProperty("AI_ACTIVATION", "off")
            .withProperty(
                "spring.autoconfigure.exclude",
                " $existingExclusion, ,$existingExclusion ",
            )

        postProcessor.postProcessEnvironment(environment, SpringApplication())
        postProcessor.postProcessEnvironment(environment, SpringApplication())

        assertEquals(
            listOf(
                existingExclusion,
                MissionAiActivationEnvironmentPostProcessor.GOOGLE_GENAI_EMBEDDING_CONNECTION_AUTO_CONFIGURATION,
            ).joinToString(","),
            environment.getProperty(
                MissionAiActivationEnvironmentPostProcessor.AUTO_CONFIGURATION_EXCLUDE_PROPERTY,
            ),
        )
    }

    @Test
    fun `invalid or differently cased activation fails fast`() {
        listOf("ON", "true", "invalid").forEach { invalid ->
            val environment = MockEnvironment().withProperty("AI_ACTIVATION", invalid)

            assertFailsWith<IllegalArgumentException> {
                postProcessor.postProcessEnvironment(environment, SpringApplication())
            }
        }
    }
}
