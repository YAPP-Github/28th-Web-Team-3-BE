package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import tools.jackson.databind.ObjectMapper

class OpenAiMissionDraftContentGeneratorTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `calls Responses API with structured output and returns generated copy`() {
        val requestBody = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val response = responsesBody(
            title = "생성된 미션 제목",
            description = "생성된 미션 설명",
        )
        val server = server { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        try {
            val properties = properties(server, requestTimeout = Duration.ofSeconds(1))
            val generator = OpenAiMissionDraftContentGenerator(
                JdkOpenAiResponsesClient(properties),
                objectMapper,
                properties,
            )

            val result = generator.generate(request())

            assertEquals("생성된 미션 제목", result.copies.single().title)
            assertEquals("생성된 미션 설명", result.copies.single().description)
            assertEquals(MissionDraftGenerationSource.OPENAI, result.source)
            assertEquals("Bearer test-api-key", authorization.get())
            assertContains(requestBody.get(), "\"model\":\"gpt-5.6-terra\"")
            assertContains(requestBody.get(), "\"type\":\"json_schema\"")
            assertContains(requestBody.get(), "\"safety_identifier\"")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `falls back to template copy when Responses API times out`() {
        val response = responsesBody("늦은 제목", "늦은 설명")
        val server = server { exchange ->
            Thread.sleep(200)
            runCatching {
                exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
        }
        try {
            val properties = properties(server, requestTimeout = Duration.ofMillis(30))
            val generator = OpenAiMissionDraftContentGenerator(
                JdkOpenAiResponsesClient(properties),
                objectMapper,
                properties,
            )

            val result = generator.generate(request())

            assertEquals("기본 제목", result.copies.single().title)
            assertEquals("기본 설명", result.copies.single().description)
            assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `falls back when structured response changes candidate identity`() {
        val invalidResponse = objectMapper.writeValueAsString(
            mapOf(
                "output" to listOf(
                    mapOf(
                        "content" to listOf(
                            mapOf(
                                "type" to "output_text",
                                "text" to objectMapper.writeValueAsString(
                                    mapOf(
                                        "items" to listOf(
                                            mapOf(
                                                "templateId" to 999,
                                                "title" to "변조 제목",
                                                "description" to "변조 설명",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val generator = OpenAiMissionDraftContentGenerator(
            OpenAiResponsesClient { invalidResponse },
            objectMapper,
            properties = OpenAiProperties(apiKey = "test-api-key", safetySalt = "test-safety-salt"),
        )

        val result = generator.generate(request())

        assertEquals("기본 제목", result.copies.single().title)
        assertEquals(1L, result.copies.single().templateId)
        assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
    }

    private fun request(): MissionDraftContentRequest =
        MissionDraftContentRequest(
            jobId = UUID.randomUUID(),
            guestUserId = 7,
            candidates = listOf(
                MissionDraftCandidate(
                    templateId = 1,
                    category = MissionCategory.MEAL,
                    templateTitle = "기본 제목",
                    templateDescription = "기본 설명",
                    actionCode = "ACTION",
                    metricType = MissionMetricType.COUNT,
                    targetCount = 1,
                    targetUnit = "TIMES_PER_WEEK",
                    estimatedSavingsWon = 5_000,
                ),
            ),
        )

    private fun responsesBody(title: String, description: String): String {
        val structuredOutput = objectMapper.writeValueAsString(
            mapOf(
                "items" to listOf(
                    mapOf(
                        "templateId" to 1,
                        "title" to title,
                        "description" to description,
                    ),
                ),
            ),
        )
        return objectMapper.writeValueAsString(
            mapOf(
                "output" to listOf(
                    mapOf(
                        "content" to listOf(
                            mapOf(
                                "type" to "output_text",
                                "text" to structuredOutput,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun properties(server: HttpServer, requestTimeout: Duration): OpenAiProperties =
        OpenAiProperties(
            baseUrl = URI.create("http://127.0.0.1:${server.address.port}"),
            apiKey = "test-api-key",
            safetySalt = "test-safety-salt",
            requestTimeout = requestTimeout,
        )

    private fun server(handler: com.sun.net.httpserver.HttpHandler): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/responses", handler)
            start()
        }
}
