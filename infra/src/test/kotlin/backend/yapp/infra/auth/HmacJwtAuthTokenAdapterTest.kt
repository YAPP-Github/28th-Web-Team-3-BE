package backend.yapp.infra.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class HmacJwtAuthTokenAdapterTest {
    private val properties = JwtProperties(
        secret = "test-secret-key-that-is-at-least-32-bytes",
        issuer = "yapp-test",
        audience = "yapp-client",
        accessTokenTtl = Duration.ofHours(1),
        refreshTokenTtl = Duration.ofHours(24),
    )
    private val now = Instant.parse("2026-07-16T00:00:00Z")

    @Test
    fun `accepts a token issued up to 60 seconds ahead`() {
        val issuer = HmacJwtAuthTokenAdapter(properties, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC))
        val verifier = HmacJwtAuthTokenAdapter(properties, Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(1L, verifier.parseAccessToken(issuer.issue(1).accessToken).guestUserId)
    }

    @Test
    fun `accepts a token expired less than 60 seconds ago`() {
        val issuer = HmacJwtAuthTokenAdapter(properties, Clock.fixed(now.minus(Duration.ofHours(1)).minusSeconds(30), ZoneOffset.UTC))
        val verifier = HmacJwtAuthTokenAdapter(properties, Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(1L, verifier.parseAccessToken(issuer.issue(1).accessToken).guestUserId)
    }
}
