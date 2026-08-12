package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.web.client.RestClient

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

    private fun properties(maxAttempts: Int = 2) = NaverBlogSearchProperties(
        baseUrl = "https://naver.example.test",
        clientId = "client-id",
        clientSecret = "client-secret",
        maxAttempts = maxAttempts,
    )

    private fun naverRequest() = RequestMatcher { request ->
        assertTrue(request.uri.path.endsWith("/search/v1/blog"))
    }
}
