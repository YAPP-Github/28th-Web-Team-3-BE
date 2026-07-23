package backend.yapp.infra.mission.generation

import java.net.URI
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("mission.generation")
data class MissionGenerationProperties(
    val provider: String = "mock",
    val executor: ExecutorProperties = ExecutorProperties(),
    val staleRunningTimeout: Duration = Duration.ofMinutes(10),
    val openai: OpenAiProperties = OpenAiProperties(),
)

data class ExecutorProperties(
    val corePoolSize: Int = 2,
    val maxPoolSize: Int = 4,
    val queueCapacity: Int = 50,
)

data class OpenAiProperties(
    val baseUrl: URI = URI.create("https://api.openai.com"),
    val apiKey: String = "",
    val safetySalt: String = "",
    val model: String = "gpt-5.6-terra",
    val reasoningEffort: String = "low",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val requestTimeout: Duration = Duration.ofSeconds(20),
    val maxOutputTokens: Int = 2_000,
)
