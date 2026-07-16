package backend.yapp.core.auth.service

import backend.yapp.core.auth.domain.GuestUser
import backend.yapp.core.auth.domain.GuestUserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class GuestUserCreator(private val guestUserRepository: GuestUserRepository) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createIfAbsent(identifierHash: String): GuestUser =
        guestUserRepository.findByIdentifierHash(identifierHash)
            ?: guestUserRepository.saveAndFlush(GuestUser(identifierHash))
}
