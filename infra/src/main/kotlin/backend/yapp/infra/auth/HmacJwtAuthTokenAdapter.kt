package backend.yapp.infra.auth

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.port.AccessTokenClaims
import backend.yapp.core.auth.port.AuthTokenPort
import backend.yapp.core.auth.port.RefreshTokenClaims
import backend.yapp.core.auth.port.TokenPair
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class HmacJwtAuthTokenAdapter(
    private val properties: JwtProperties,
    private val clock: Clock,
) : AuthTokenPort {
    private val clockSkew = Duration.ofSeconds(60)
    private val secret = properties.secret.toByteArray(Charsets.UTF_8).also {
        require(it.size >= 32) { "jwt.secret must be at least 32 bytes" }
    }

    override fun issue(guestUserId: Long): TokenPair =
        TokenPair(
            accessToken = create(guestUserId, "access", properties.accessTokenTtl),
            refreshToken = create(guestUserId, "refresh", properties.refreshTokenTtl, UUID.randomUUID()),
        )

    override fun parseRefreshToken(token: String): RefreshTokenClaims {
        val claims = parse(token, "refresh")
        return RefreshTokenClaims(
            guestUserId = claims.subject.toLongOrNull() ?: unauthorized(),
            tokenId = claims.jwtid?.let { runCatching { UUID.fromString(it) }.getOrElse { unauthorized() } } ?: unauthorized(),
            expiresAt = claims.expirationTime.toInstant(),
        )
    }

    override fun parseAccessToken(token: String): AccessTokenClaims {
        val claims = parse(token, "access")
        return AccessTokenClaims(claims.subject.toLongOrNull() ?: unauthorized())
    }

    private fun create(userId: Long, type: String, ttl: java.time.Duration, tokenId: UUID? = null): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer(properties.issuer)
            .audience(properties.audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .claim("type", type)
            .apply { tokenId?.let { jwtID(it.toString()) } }
            .build()
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).also { it.sign(MACSigner(secret)) }.serialize()
    }

    private fun parse(token: String, expectedType: String): JWTClaimsSet = try {
        val signedJwt = SignedJWT.parse(token)
        if (signedJwt.header.algorithm != JWSAlgorithm.HS256 || !signedJwt.verify(MACVerifier(secret))) unauthorized()
        val claims = signedJwt.jwtClaimsSet
        val now = clock.instant()
        if (
            claims.issuer != properties.issuer ||
            !claims.audience.contains(properties.audience) ||
            claims.issueTime == null || claims.expirationTime == null ||
            claims.issueTime.toInstant().minus(clockSkew).isAfter(now) ||
            !claims.expirationTime.toInstant().isAfter(claims.issueTime.toInstant()) ||
            !claims.expirationTime.toInstant().plus(clockSkew).isAfter(now) ||
            claims.getStringClaim("type") != expectedType
        ) unauthorized()
        claims
    } catch (_: Exception) {
        unauthorized()
    }

    private fun unauthorized(): Nothing = throw BaseException(ErrorCode.UNAUTHORIZED)
}
