package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import backend.yapp.core.mission.generation.port.MissionBlogSearchResult
import java.net.URI
import org.springframework.web.client.RestClient
import org.springframework.web.util.HtmlUtils

class NaverBlogSearchAdapter(
    builder: RestClient.Builder,
    private val properties: NaverBlogSearchProperties,
) : MissionBlogSearchPort {
    private val client = builder.baseUrl(properties.baseUrl).build()

    override fun search(query: String, count: Int): List<MissionBlogSearchResult> {
        require(properties.clientId.isNotBlank() && properties.clientSecret.isNotBlank()) {
            "Naver Blog Search credentials are not configured"
        }
        var attempt = 1
        while (true) {
            try {
                val response = client.get()
                    .uri { uriBuilder ->
                        uriBuilder.path("/search/v1/blog")
                            .queryParam("query", query)
                            .queryParam("display", count.coerceIn(1, 100))
                            .queryParam("start", 1)
                            .queryParam("sort", "sim")
                            .queryParam("format", "json")
                            .build()
                    }
                    .header("X-NCP-APIGW-API-KEY-ID", properties.clientId)
                    .header("X-NCP-APIGW-API-KEY", properties.clientSecret)
                    .retrieve()
                    .body(NaverBlogSearchResponse::class.java)
                    ?: return emptyList()
                return response.items.asSequence()
                    .mapNotNull(::normalize)
                    .distinctBy(MissionBlogSearchResult::url)
                    .take(count)
                    .toList()
            } catch (exception: Exception) {
                if (attempt >= properties.maxAttempts) throw exception
                attempt++
            }
        }
    }

    private fun normalize(item: NaverBlogSearchItem): MissionBlogSearchResult? {
        val uri = runCatching { URI(item.link) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val title = clean(item.title).take(300)
        val description = clean(item.description).take(1000)
        val source = clean(item.bloggername).take(200)
        if (title.isBlank() || item.link.length > 1000) return null
        return MissionBlogSearchResult(title, description, source.ifBlank { uri.host }, item.link)
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

data class NaverBlogSearchResponse(val items: List<NaverBlogSearchItem> = emptyList())

data class NaverBlogSearchItem(
    val title: String = "",
    val link: String = "",
    val description: String = "",
    val bloggername: String = "",
)
