package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

class NaverBlogSearchAdapterTest {
    @Test
    fun `does not send a request when either credential is blank`() {
        val adapter = NaverBlogSearchAdapter(
            RestClient.builder(),
            NaverBlogSearchProperties(
                baseUrl = "http://127.0.0.1:1",
                clientId = "",
                clientSecret = "secret",
            ),
        )

        val outcome = adapter.search("sensitive query must not be logged", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.CREDENTIALS_MISSING, attempts = 0),
            outcome,
        )
    }

    @Test
    fun `trims surrounding whitespace from credentials before creating HTTP headers`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = NaverBlogSearchAdapter(
            builder,
            NaverBlogSearchProperties(
                baseUrl = "https://naver.example.test",
                clientId = " client-id\n",
                clientSecret = "client-secret\n",
            ),
        )
        server.expect(naverRequest())
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))

        val outcome = adapter.search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT, outcome.category)
        server.verify()
    }

    @Test
    fun `classifies a non-empty provider response as success after normalization`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = NaverBlogSearchAdapter(builder, properties())
        server.expect(naverRequest())
            .andRespond(
                withSuccess(
                    """{"items":[{"title":"<b>절약</b> 팁","link":"https://blog.example.test/1","description":"설명","bloggername":"작성자"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val outcome = adapter.search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.SUCCESS, outcome.category)
        assertEquals(1, (outcome as MissionBlogSearchOutcome.Completed).providerItemCount)
        assertEquals(1, outcome.results.size)
        server.verify()
    }

    @Test
    fun `encodes the Korean mission query before sending the request`() {
        val query = "20대 서울 배달음식 절약 팁"
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = NaverBlogSearchAdapter(builder, properties())
        server.expect(naverRequest(query))
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))

        val outcome = adapter.search(query, 15)

        assertEquals(MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT, outcome.category)
        server.verify()
    }

    @Test
    fun `deserializes the complete provider response including metadata fields`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = NaverBlogSearchAdapter(builder, properties())
        server.expect(naverRequest())
            .andRespond(
                withSuccess(
                    """
                    {
                      "lastBuildDate":"Thu, 11 Jun 2026 19:14:42 +0900",
                      "total":1,
                      "start":1,
                      "display":1,
                      "items":[{
                        "title":"<b>절약</b> 팁",
                        "link":"https://blog.example.test/1",
                        "description":"설명",
                        "bloggername":"작성자",
                        "bloggerlink":"blog.example.test",
                        "postdate":"20260611"
                      }]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val outcome = adapter.search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.SUCCESS, outcome.category)
        assertEquals(1, (outcome as MissionBlogSearchOutcome.Completed).results.size)
        server.verify()
    }

    @Test
    fun `keeps valid results when optional provider fields are null or another item is malformed`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = NaverBlogSearchAdapter(builder, properties())
        server.expect(naverRequest())
            .andRespond(
                withSuccess(
                    """
                    {
                      "items":[
                        {
                          "title":"절약 팁",
                          "link":"https://blog.example.test/valid",
                          "description":null,
                          "bloggername":null
                        },
                        {
                          "title":null,
                          "link":"https://blog.example.test/invalid",
                          "description":"설명",
                          "bloggername":"작성자"
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val outcome = adapter.search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.SUCCESS, outcome.category)
        val results = (outcome as MissionBlogSearchOutcome.Completed).results
        assertEquals(1, results.size)
        assertEquals("", results.single().description)
        assertEquals("blog.example.test", results.single().source)
        server.verify()
    }

    @Test
    fun `distinguishes empty provider results and all normalized results excluded`() {
        val emptyBuilder = RestClient.builder()
        val emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build()
        emptyServer.expect(naverRequest())
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))
        val emptyOutcome = NaverBlogSearchAdapter(emptyBuilder, properties()).search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT, emptyOutcome.category)
        emptyServer.verify()

        val invalidBuilder = RestClient.builder()
        val invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build()
        invalidServer.expect(naverRequest())
            .andRespond(
                withSuccess(
                    """{"items":[{"title":"","link":"not-a-url","description":"설명","bloggername":"작성자"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val invalidOutcome = NaverBlogSearchAdapter(invalidBuilder, properties()).search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.ALL_NORMALIZED_OUT, invalidOutcome.category)
        invalidServer.verify()
    }

    @Test
    fun `classifies authorization response without retrying`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(naverRequest())
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val outcome = NaverBlogSearchAdapter(builder, properties(maxAttempts = 2)).search("query", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.AUTHORIZATION, attempts = 1),
            outcome,
        )
        server.verify()
    }

    @Test
    fun `classifies forbidden response as authorization without retrying`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(naverRequest())
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val outcome = NaverBlogSearchAdapter(builder, properties(maxAttempts = 2)).search("query", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.AUTHORIZATION, attempts = 1),
            outcome,
        )
        server.verify()
    }

    @Test
    fun `retries rate limit and server errors then returns their category`() {
        val rateBuilder = RestClient.builder()
        val rateServer = MockRestServiceServer.bindTo(rateBuilder).build()
        rateServer.expect(naverRequest()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        rateServer.expect(naverRequest()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        val rateOutcome = NaverBlogSearchAdapter(rateBuilder, properties(maxAttempts = 2)).search("query", 15)

        assertEquals(MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.RATE_LIMIT, 2), rateOutcome)
        rateServer.verify()

        val serverBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(serverBuilder).build()
        server.expect(naverRequest()).andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        server.expect(naverRequest()).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        val serverOutcome = NaverBlogSearchAdapter(serverBuilder, properties(maxAttempts = 2)).search("query", 15)

        assertEquals(MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.HTTP_5XX, 2), serverOutcome)
        server.verify()
    }

    @Test
    fun `retries transport failure and classifies malformed JSON`() {
        val retryBuilder = RestClient.builder()
        val retryServer = MockRestServiceServer.bindTo(retryBuilder).build()
        retryServer.expect(naverRequest()).andRespond(withException(java.net.SocketTimeoutException("timeout")))
        retryServer.expect(naverRequest()).andRespond(
            withSuccess(
                """{"items":[{"title":"절약","link":"https://blog.example.test/1"}]}""",
                MediaType.APPLICATION_JSON,
            ),
        )

        val retryOutcome = NaverBlogSearchAdapter(retryBuilder, properties(maxAttempts = 2)).search("query", 15)

        assertEquals(MissionBlogSearchOutcomeCategory.SUCCESS, retryOutcome.category)
        retryServer.verify()

        val invalidBuilder = RestClient.builder()
        val invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build()
        invalidServer.expect(naverRequest()).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON))

        val invalidOutcome = NaverBlogSearchAdapter(invalidBuilder, properties()).search("query", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.RESPONSE_DESERIALIZATION, 1),
            invalidOutcome,
        )
        invalidServer.verify()
    }

    @Test
    fun `distinguishes unsupported response content from an unexpected client defect`() {
        val contentBuilder = RestClient.builder()
        val contentServer = MockRestServiceServer.bindTo(contentBuilder).build()
        contentServer.expect(naverRequest())
            .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN))

        val contentOutcome = NaverBlogSearchAdapter(contentBuilder, properties()).search("query", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.RESPONSE_DESERIALIZATION, 1),
            contentOutcome,
        )
        contentServer.verify()

        val defectBuilder = RestClient.builder()
        val defectServer = MockRestServiceServer.bindTo(defectBuilder).build()
        defectServer.expect(naverRequest()).andRespond { throw RestClientException("client defect") }

        val defectOutcome = NaverBlogSearchAdapter(defectBuilder, properties()).search("query", 15)

        assertEquals(
            MissionBlogSearchOutcome.Failed(MissionBlogSearchOutcomeCategory.UNEXPECTED_INTERNAL, 1),
            defectOutcome,
        )
        defectServer.verify()
    }

    private fun properties(maxAttempts: Int = 2) = NaverBlogSearchProperties(
        baseUrl = "https://naver.example.test",
        clientId = "client-id",
        clientSecret = "client-secret",
        maxAttempts = maxAttempts,
    )

    private fun naverRequest(expectedQuery: String = "query") = RequestMatcher { request ->
        assertEquals(HttpMethod.GET, request.method)
        assertTrue(request.uri.path.endsWith("/search/v1/blog"))
        val query = UriComponentsBuilder.fromUri(request.uri).build().queryParams
        assertEquals(
            expectedQuery,
            URLDecoder.decode(query.getFirst("query"), StandardCharsets.UTF_8),
        )
        assertEquals("15", query.getFirst("display"))
        assertEquals("1", query.getFirst("start"))
        assertEquals("sim", query.getFirst("sort"))
        assertEquals("json", query.getFirst("format"))
        assertEquals("client-id", request.headers.getFirst("X-NCP-APIGW-API-KEY-ID"))
        assertEquals("client-secret", request.headers.getFirst("X-NCP-APIGW-API-KEY"))
    }
}
