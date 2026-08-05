package backend.yapp.core.auth.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.auth.domain.GuestUser
import backend.yapp.core.auth.domain.GuestUserRepository
import backend.yapp.core.auth.domain.RefreshToken
import backend.yapp.core.auth.domain.RefreshTokenRepository
import backend.yapp.core.auth.port.AuthTokenPort
import backend.yapp.core.auth.port.TokenPair
import backend.yapp.core.auth.port.ValueHashPort
import java.time.Clock
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuestAuthService(
    private val guestUserRepository: GuestUserRepository,
    private val guestUserCreator: GuestUserCreator,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenPort: AuthTokenPort,
    private val valueHashPort: ValueHashPort,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun issueForIdentifier(identifier: String): TokenPair {
        val identifierHash = valueHashPort.hash(identifier)
        val guestUser = guestUserRepository.findByIdentifierHash(identifierHash)
            ?: createOrFind(identifierHash)
        return issueAndStore(guestUser)
    }

    @Transactional
    fun rotate(refreshToken: String): TokenPair {
        val claims = tokenPort.parseRefreshToken(refreshToken)
        val consumed = refreshTokenRepository.consume(
            tokenHash = valueHashPort.hash(refreshToken),
            tokenId = claims.tokenId,
            guestUserId = claims.guestUserId,
            now = clock.instant(),
        )
        if (consumed != 1) throw BaseException(ErrorCode.UNAUTHORIZED)

        val guestUser = guestUserRepository.findById(claims.guestUserId)
            .orElseThrow { BaseException(ErrorCode.UNAUTHORIZED) }
        return issueAndStore(guestUser)
    }

    fun authenticate(accessToken: String): Long {
        val guestUserId = tokenPort.parseAccessToken(accessToken).guestUserId
        if (!guestUserRepository.existsById(guestUserId)) throw BaseException(ErrorCode.UNAUTHORIZED)
        return guestUserId
    }

    private fun createOrFind(identifierHash: String): GuestUser = try {
        guestUserCreator.createIfAbsent(identifierHash)
    } catch (_: DataIntegrityViolationException) {
        guestUserRepository.findByIdentifierHash(identifierHash)
            ?: throw BaseException(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun issueAndStore(guestUser: GuestUser): TokenPair {
        val pair = tokenPort.issue(guestUser.id)
        val claims = tokenPort.parseRefreshToken(pair.refreshToken)
        refreshTokenRepository.save(
            RefreshToken(
                id = claims.tokenId,
                guestUser = guestUser,
                tokenHash = valueHashPort.hash(pair.refreshToken),
                expiresAt = claims.expiresAt,
            ),
        )
        return pair
    }
}
