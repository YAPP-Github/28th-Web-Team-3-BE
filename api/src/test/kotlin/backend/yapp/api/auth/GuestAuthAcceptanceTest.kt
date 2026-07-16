package backend.yapp.api.auth

import backend.yapp.core.auth.fixture.GuestAuthFixture
import backend.yapp.core.auth.port.AuthTokenPort
import org.hamcrest.Matchers.not
import org.assertj.core.api.Assertions.assertThat
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestAuthAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenPort: AuthTokenPort,
) {
    @Test
    fun `same UUID maps to the same guest subject`() {
        val first = issue(GuestAuthFixture.IDENTIFIER)
        val second = issue(GuestAuthFixture.IDENTIFIER)

        assertThat(tokenPort.parseAccessToken(first).guestUserId)
            .isEqualTo(tokenPort.parseAccessToken(second).guestUserId)
    }

    @Test
    fun `refresh rotates token and rejects replay`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        val rotated = mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken", not(refreshToken)))
            .andReturn().response.contentAsString

        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isUnauthorized)

        assertThat(rotated).isNotBlank()
    }

    @Test
    fun `refresh token cannot authenticate bearer request`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        mockMvc.perform(
            get("/api/unknown")
                .header("Authorization", "Bearer $refreshToken"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `only one simultaneous refresh can consume the same token`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val statuses = executor.invokeAll(List(2) {
                Callable {
                    mockMvc.perform(
                        post("/api/auth/guest/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"$refreshToken\"}"),
                    ).andReturn().response.status
                }
            }).map { it.get() }
            assertThat(statuses).containsExactlyInAnyOrder(200, 401)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `simultaneous first requests for the same UUID both succeed`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val statuses = executor.invokeAll(List(2) {
                Callable {
                    mockMvc.perform(
                        post("/api/auth/guest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"uuid\":\"c4ffb5fe-9fb5-4f9d-aa26-9c31c66cc4f1\"}"),
                    ).andReturn().response.status
                }
            }).map { it.get() }
            assertThat(statuses).containsOnly(201)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `invalid identifier is rejected`() {
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"not-a-uuid\"}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `access token cannot be used to refresh`() {
        val accessToken = issue(GuestAuthFixture.IDENTIFIER)
        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$accessToken\"}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `expired access token header does not block refresh endpoint`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .header("Authorization", "Bearer ${expiredAccessToken()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `missing request value is rejected as bad request`() {
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `expired or forged refresh token is rejected with security JSON`() {
        listOf(expiredRefreshToken(), forgedRefreshToken()).forEach { token ->
            mockMvc.perform(
                post("/api/auth/guest/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"$token\"}"),
            ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.name").value("UNAUTHORIZED"))
        }
    }

    @Test
    fun `OpenAPI document publishes guest authentication contract`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auth/guest'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/guest/refresh'].post.responses['401']").exists())
    }

    private fun issue(identifier: String): String =
        JsonPath.read(issueWithResponse(identifier).response.contentAsString, "$.accessToken")

    private fun refreshTokenOf(responseJson: String): String = JsonPath.read(responseJson, "$.refreshToken")

    private fun issueWithResponse(identifier: String) =
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"$identifier\"}"),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

    private fun expiredRefreshToken(): String = signedToken(
        secret = "test-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().minusSeconds(60),
        type = "refresh",
    )

    private fun forgedRefreshToken(): String = signedToken(
        secret = "forged-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().plusSeconds(3600),
        type = "refresh",
    )

    private fun expiredAccessToken(): String = signedToken(
        secret = "test-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().minusSeconds(60),
        type = "access",
    )

    private fun signedToken(secret: String, expiresAt: Instant, type: String): String {
        val now = Instant.now().minusSeconds(120)
        val claims = JWTClaimsSet.Builder()
            .subject("1").issuer("yapp-test").audience("yapp-client")
            .issueTime(Date.from(now)).expirationTime(Date.from(expiresAt))
            .jwtID("8d1ec76a-9df0-43d0-89b8-aedec558dc23").claim("type", type).build()
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).also {
            it.sign(MACSigner(secret.toByteArray()))
        }.serialize()
    }
}
