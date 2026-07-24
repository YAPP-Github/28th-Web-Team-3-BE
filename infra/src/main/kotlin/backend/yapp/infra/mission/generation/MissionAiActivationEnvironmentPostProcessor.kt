package backend.yapp.infra.mission.generation

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class MissionAiActivationEnvironmentPostProcessor : EnvironmentPostProcessor, Ordered {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val activation = environment.getProperty(ACTIVATION_ENV)?.ifBlank { null } ?: OFF
        require(activation == ON || activation == OFF) {
            "$ACTIVATION_ENV must be exactly '$ON' or '$OFF'"
        }
        val model = if (activation == ON) GOOGLE_GENAI else NONE
        environment.propertySources.addFirst(
            MapPropertySource(
                PROPERTY_SOURCE_NAME,
                mapOf(
                    ACTIVATION_PROPERTY to activation,
                    CHAT_MODEL_PROPERTY to model,
                    EMBEDDING_MODEL_PROPERTY to model,
                ),
            ),
        )
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    companion object {
        const val ACTIVATION_ENV = "AI_ACTIVATION"
        const val ACTIVATION_PROPERTY = "mission.generation.ai-activation"
        const val CHAT_MODEL_PROPERTY = "spring.ai.model.chat"
        const val EMBEDDING_MODEL_PROPERTY = "spring.ai.model.embedding.text"
        const val ON = "on"
        const val OFF = "off"

        private const val GOOGLE_GENAI = "google-genai"
        private const val NONE = "none"
        private const val PROPERTY_SOURCE_NAME = "missionAiActivation"
    }
}
