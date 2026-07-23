package backend.yapp.infra.mission.generation

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.http.MediaType

fun interface OpenAiResponsesClient {
    fun create(requestBody: String): String
}

class JdkOpenAiResponsesClient(
    private val properties: OpenAiProperties,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout)
        .build(),
) : OpenAiResponsesClient {
    override fun create(requestBody: String): String {
        require(properties.apiKey.isNotBlank()) { "OPENAI_API_KEY is required for the openai provider" }
        val request = HttpRequest.newBuilder(responsesUri())
            .timeout(properties.requestTimeout)
            .header("Authorization", "Bearer ${properties.apiKey}")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "OpenAI Responses API returned HTTP ${response.statusCode()}"
        }
        return response.body()
    }

    private fun responsesUri(): URI =
        properties.baseUrl.toString().trimEnd('/').let { URI.create("$it/v1/responses") }
}
