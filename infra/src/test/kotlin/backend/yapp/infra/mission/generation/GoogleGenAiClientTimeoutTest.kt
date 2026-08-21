package backend.yapp.infra.mission.generation

import com.google.genai.Client
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class GoogleGenAiClientTimeoutTest {
    @Test
    fun `SDK transport applies the configured total timeout without retries`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setBodyDelay(1, TimeUnit.SECONDS)
                    .setBody("{}"),
            )
            Client.builder()
                .apiKey("test-key")
                .httpOptions(
                    HttpOptions.builder()
                        .baseUrl(server.url("/").toString())
                        .timeout(200)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build(),
                )
                .build()
                .use { client ->
                    val startedAt = System.nanoTime()

                    assertFails {
                        client.models.generateContent("test-model", "test input", null)
                    }

                    val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
                    assertTrue(elapsed < Duration.ofMillis(800), "SDK timeout was not applied: $elapsed")
                    assertTrue(server.requestCount == 1, "SDK retried despite attempts=1")
                }
        }
    }
}
