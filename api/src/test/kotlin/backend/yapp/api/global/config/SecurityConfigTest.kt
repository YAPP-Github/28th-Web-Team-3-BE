package backend.yapp.api.global.config

import backend.yapp.core.auth.service.GuestAuthService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import tools.jackson.databind.ObjectMapper

class SecurityConfigTest {
    @Test
    fun `unsupported application role fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SecurityConfig(
                guestAuthService = mock(GuestAuthService::class.java),
                objectMapper = ObjectMapper(),
                appRole = "unknown",
            )
        }
    }
}
