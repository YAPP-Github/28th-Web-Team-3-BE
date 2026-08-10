package backend.yapp.api.global.config

import backend.yapp.core.auth.service.GuestAuthService
import jakarta.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
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

    @Test
    fun `authenticated request exposes guest user id through MDC only while processing the request`() {
        val guestAuthService = mock(GuestAuthService::class.java)
        `when`(guestAuthService.authenticate("access-token")).thenReturn(42L)
        val filter = BearerTokenFilter(guestAuthService, ObjectMapper(), "api")
        val request = MockHttpServletRequest("GET", "/api/users/me").apply {
            addHeader("Authorization", "Bearer access-token")
        }
        var guestUserIdInRequest: String? = null

        filter.doFilter(
            request,
            MockHttpServletResponse(),
            FilterChain { _, _ -> guestUserIdInRequest = MDC.get(BearerTokenFilter.GUEST_USER_ID_MDC_KEY) },
        )

        assertEquals("42", guestUserIdInRequest)
        assertNull(MDC.get(BearerTokenFilter.GUEST_USER_ID_MDC_KEY))
    }

    @Test
    fun `guest user id is removed from MDC when request processing fails`() {
        val guestAuthService = mock(GuestAuthService::class.java)
        `when`(guestAuthService.authenticate("access-token")).thenReturn(42L)
        val filter = BearerTokenFilter(guestAuthService, ObjectMapper(), "api")
        val request = MockHttpServletRequest("GET", "/api/users/me").apply {
            addHeader("Authorization", "Bearer access-token")
        }

        assertFailsWith<IllegalStateException> {
            filter.doFilter(
                request,
                MockHttpServletResponse(),
                FilterChain { _, _ -> throw IllegalStateException("request failed") },
            )
        }

        assertNull(MDC.get(BearerTokenFilter.GUEST_USER_ID_MDC_KEY))
    }

    @Test
    fun `public request leaves guest user id absent for the anonymous log pattern fallback`() {
        val filter = BearerTokenFilter(mock(GuestAuthService::class.java), ObjectMapper(), "api")
        val request = MockHttpServletRequest("POST", "/api/auth/guest")
        var guestUserIdInRequest: String? = "not-checked"

        filter.doFilter(
            request,
            MockHttpServletResponse(),
            FilterChain { _, _ -> guestUserIdInRequest = MDC.get(BearerTokenFilter.GUEST_USER_ID_MDC_KEY) },
        )

        assertNull(guestUserIdInRequest)
    }
}
