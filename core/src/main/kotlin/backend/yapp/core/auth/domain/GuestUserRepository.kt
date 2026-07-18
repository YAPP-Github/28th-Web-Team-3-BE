package backend.yapp.core.auth.domain

import org.springframework.data.jpa.repository.JpaRepository

interface GuestUserRepository : JpaRepository<GuestUser, Long> {
    fun findByIdentifierHash(identifierHash: String): GuestUser?
}
