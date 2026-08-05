package backend.yapp.core.auth.domain

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    @Modifying
    @Query("delete from RefreshToken token where token.guestUser.id = :guestUserId")
    fun deleteByGuestUserId(@Param("guestUserId") guestUserId: Long): Int

    @Modifying
    @Query("""
        delete from RefreshToken token
        where token.tokenHash = :tokenHash
          and token.id = :tokenId
          and token.guestUser.id = :guestUserId
          and token.expiresAt > :now
    """)
    fun consume(
        @Param("tokenHash") tokenHash: String,
        @Param("tokenId") tokenId: UUID,
        @Param("guestUserId") guestUserId: Long,
        @Param("now") now: Instant,
    ): Int
}
