package backend.yapp.core.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "guest_user")
class GuestUser(
    @Column(name = "identifier_hash", nullable = false, unique = true, length = 64)
    val identifierHash: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
