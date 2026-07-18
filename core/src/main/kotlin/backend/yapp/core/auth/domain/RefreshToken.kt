package backend.yapp.core.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_token")
class RefreshToken(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_user_id", nullable = false)
    val guestUser: GuestUser,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
