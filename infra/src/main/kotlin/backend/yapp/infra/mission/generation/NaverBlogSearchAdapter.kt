package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import backend.yapp.core.mission.generation.port.MissionBlogSearchResult
import java.net.URI
import java.time.Duration
import java.time.Instant
import org.springframework.http.HttpStatusCode
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.UnknownContentTypeException
import org.springframework.web.util.HtmlUtils
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

class NaverBlogSearchAdapter(
    builder: RestClient.Builder,
    private val properties: NaverBlogSearchProperties,
    private val objectMapper: ObjectMapper,
    private val telemetry: NaverBlogSearchTelemetry = NoopNaverBlogSearchTelemetry,
) : MissionBlogSearchPort {
    private val client = builder.baseUrl(properties.baseUrl).build()
    private val clientId = properties.clientId.trim()
    private val clientSecret = properties.clientSecret.trim()

    override fun search(query: String, count: Int): MissionBlogSearchOutcome {
        val startedAt = Instant.now()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return MissionBlogSearchOutcome.Failed(
                MissionBlogSearchOutcomeCategory.CREDENTIALS_MISSING,
                attempts = 0,
            ).also { outcome ->
                telemetry.failed(outcome, credentialsConfigured = false, Duration.between(startedAt, Instant.now()))
            }
        }
        var attempt = 1
        while (true) {
            try {
                val responseBody = client.get()
                    .uri { uriBuilder ->
                        uriBuilder.path("/search/v1/blog")
                            .queryParam("query", query)
                            .queryParam("display", count.coerceIn(1, 100))
                            .queryParam("start", 1)
                            .queryParam("sort", "sim")
                            .queryParam("format", "json")
                            .build()
                    }
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { _, clientResponse ->
                        throw NaverBlogHttpStatusException(clientResponse.statusCode.value())
                    }
                    .body(String::class.java)
                val response = responseBody?.let {
                    objectMapper.readValue(it, NaverBlogSearchResponse::class.java)
                } ?: NaverBlogSearchResponse()
                val results = response.items.asSequence()
                    .mapNotNull(::normalize)
                    .distinctBy(MissionBlogSearchResult::url)
                    .take(count)
                    .toList()
                val category = when {
                    response.items.isEmpty() -> MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT
                    results.isEmpty() -> MissionBlogSearchOutcomeCategory.ALL_NORMALIZED_OUT
                    else -> MissionBlogSearchOutcomeCategory.SUCCESS
                }
                return MissionBlogSearchOutcome.Completed(category, response.items.size, results).also { outcome ->
                    telemetry.completed(outcome, attempt, Duration.between(startedAt, Instant.now()))
                }
            } catch (exception: Exception) {
                val category = NaverBlogSearchFailureClassifier.classify(exception)
                if (attempt >= properties.maxAttempts || !category.retryable) {
                    return MissionBlogSearchOutcome.Failed(category.category, attempt).also { outcome ->
                        telemetry.failed(
                            outcome,
                            credentialsConfigured = true,
                            Duration.between(startedAt, Instant.now()),
                            cause = exception,
                        )
                    }
                }
                attempt++
            }
        }
    }

    private fun normalize(item: NaverBlogSearchItem): MissionBlogSearchResult? {
        val link = item.link?.trim().orEmpty()
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val title = clean(item.title.orEmpty()).take(300)
        val description = clean(item.description.orEmpty()).take(1000)
        val source = clean(item.bloggername.orEmpty()).take(200)
        if (title.isBlank() || link.length > 1000) return null
        return MissionBlogSearchResult(title, description, source.ifBlank { uri.host }, link)
    }

    private fun clean(value: String): String =
        HtmlUtils.htmlUnescape(value.replace(HTML_TAG, " "))
            .replace(WHITESPACE, " ")
            .trim()

    companion object {
        private val HTML_TAG = Regex("<[^>]+>")
        private val WHITESPACE = Regex("\\s+")
    }
}

private class NaverBlogHttpStatusException(
    val statusCode: Int,
) : RuntimeException("Naver Blog Search returned HTTP $statusCode")

private data class NaverBlogSearchFailureClassification(
    val category: MissionBlogSearchOutcomeCategory,
    val retryable: Boolean,
)

private object NaverBlogSearchFailureClassifier {
    fun classify(error: Throwable): NaverBlogSearchFailureClassification {
        val causes = generateSequence(error) { it.cause }.toList()
        val statusCode = causes.filterIsInstance<NaverBlogHttpStatusException>().firstOrNull()?.statusCode
            ?: causes.filterIsInstance<RestClientResponseException>().firstOrNull()?.statusCode?.value()
        if (statusCode != null) return fromStatusCode(statusCode)
        if (
            causes.any { cause ->
                cause is HttpMessageConversionException ||
                    cause is UnknownContentTypeException ||
                    cause is JacksonException
            }
        ) {
            return NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.RESPONSE_DESERIALIZATION, false)
        }
        if (causes.any { it is ResourceAccessException || it is java.io.IOException || it is java.net.SocketTimeoutException }) {
            return NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.NETWORK_TIMEOUT, true)
        }
        return NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.UNEXPECTED_INTERNAL, false)
    }

    private fun fromStatusCode(statusCode: Int): NaverBlogSearchFailureClassification = when (statusCode) {
        401, 403 -> NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.AUTHORIZATION, false)
        429 -> NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.RATE_LIMIT, true)
        in 400..499 -> NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.HTTP_4XX_OTHER, false)
        in 500..599 -> NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.HTTP_5XX, true)
        else -> NaverBlogSearchFailureClassification(MissionBlogSearchOutcomeCategory.UNEXPECTED_INTERNAL, false)
    }
}

data class NaverBlogSearchResponse(val items: List<NaverBlogSearchItem> = emptyList())

data class NaverBlogSearchItem(
    val title: String? = null,
    val link: String? = null,
    val description: String? = null,
    val bloggername: String? = null,
)
