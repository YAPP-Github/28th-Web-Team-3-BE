package backend.yapp.api.auth

import backend.yapp.core.auth.port.AuthTokenPort
import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentUserAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenPort: AuthTokenPort,
) {
    @Test
    fun `access token returns the current user ID and onboarding completion status`() {
        val token = issueGuestToken()
        val userId = tokenPort.parseAccessToken(token).guestUserId

        currentUser(token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.onboardingCompleted").value(false))

        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"monthlySavingManwon":100,"goalPeriodMonths":24,"address":"SEOUL"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/onboarding/goal")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PLAN_1"}"""),
        ).andExpect(status().isCreated)

        currentUser(token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.onboardingCompleted").value(true))
    }

    @Test
    fun `current user lookup requires a valid access token`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `OpenAPI document publishes the current user contract`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].accessTokenAuth").exists())
    }

    private fun currentUser(token: String) =
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $token"))

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
    }
}
