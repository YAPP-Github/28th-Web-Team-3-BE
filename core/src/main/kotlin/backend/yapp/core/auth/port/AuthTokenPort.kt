package backend.yapp.core.auth.port

import java.time.Instant
import java.util.UUID

data class TokenPair(val accessToken: String, val refreshToken: String)
data class RefreshTokenClaims(val guestUserId: Long, val tokenId: UUID, val expiresAt: Instant)
data class AccessTokenClaims(val guestUserId: Long)

interface AuthTokenPort {
    fun issue(guestUserId: Long): TokenPair
    fun parseRefreshToken(token: String): RefreshTokenClaims
    fun parseAccessToken(token: String): AccessTokenClaims
}
