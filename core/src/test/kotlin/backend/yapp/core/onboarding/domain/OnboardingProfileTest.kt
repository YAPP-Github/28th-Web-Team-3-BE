package backend.yapp.core.onboarding.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingProfileTest {
    @Test
    fun `missing address is normalized to Seoul when the profile is loaded`() {
        val profile = OnboardingProfile(guestUserId = 1L, address = null)

        profile.defaultAddressIfMissing()

        assertEquals(ResidentialArea.SEOUL, profile.address)
    }
}
