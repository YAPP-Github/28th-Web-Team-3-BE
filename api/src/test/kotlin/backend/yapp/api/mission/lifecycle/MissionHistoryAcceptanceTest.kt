package backend.yapp.api.mission.lifecycle

import com.jayway.jsonpath.JsonPath
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MissionHistoryAcceptanceTest.FixedClockConfig::class)
class MissionHistoryAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @TestConfiguration
    class FixedClockConfig {
        @Bean
        @Primary
        fun missionHistoryTestClock(): Clock =
            Clock.fixed(Instant.parse("2026-09-02T03:00:00Z"), ZoneOffset.UTC)
    }

    @Test
    fun `current month history returns all calendar weeks and counts for authenticated user`() {
        val token = guestToken()
        createManual(token)

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .param("month", "9")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.histories.length()").value(4))
            .andExpect(jsonPath("$.histories[0].weekOfMonth").value(1))
            .andExpect(jsonPath("$.histories[0].weekStartDate").value("2026-08-31"))
            .andExpect(jsonPath("$.histories[0].weekEndDate").value("2026-09-06"))
            .andExpect(jsonPath("$.histories[0].completedCount").value(0))
            .andExpect(jsonPath("$.histories[0].totalCount").value(1))
            .andExpect(jsonPath("$.histories[0].isCurrentWeek").value(true))
            .andExpect(jsonPath("$.histories[1].completedCount").value(0))
            .andExpect(jsonPath("$.histories[1].totalCount").value(0))
            .andExpect(jsonPath("$.histories[1].isCurrentWeek").value(false))
            .andExpect(jsonPath("$.histories[0].progressPercent").doesNotExist())
            .andExpect(jsonPath("$.histories[0].estimatedSavingsWon").doesNotExist())
    }

    @Test
    fun `history is isolated by authenticated user`() {
        val ownerToken = guestToken()
        val otherToken = guestToken()
        createManual(ownerToken)

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .param("month", "9")
                .header(AUTHORIZATION, "Bearer $otherToken"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.histories[0].totalCount").value(0))
    }

    @Test
    fun `august first and second weeks are synthetic zero histories`() {
        val token = guestToken()

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .param("month", "8")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.histories.length()").value(4))
            .andExpect(jsonPath("$.histories[0].completedCount").value(0))
            .andExpect(jsonPath("$.histories[0].totalCount").value(0))
            .andExpect(jsonPath("$.histories[1].completedCount").value(0))
            .andExpect(jsonPath("$.histories[1].totalCount").value(0))
    }

    @Test
    fun `invalid future and unavailable periods return mission history errors`() {
        val token = guestToken()

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "invalid")
                .param("month", "9")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_HISTORY_INVALID_PERIOD"))

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_HISTORY_INVALID_PERIOD"))

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .param("month", "10")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_HISTORY_INVALID_PERIOD"))

        mockMvc.perform(
            get("/api/missions/histories")
                .param("year", "2026")
                .param("month", "7")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_HISTORY_NOT_AVAILABLE"))
    }

    @Test
    fun `history endpoint requires authentication and is published in openapi`() {
        mockMvc.perform(get("/api/missions/histories").param("year", "2026").param("month", "9"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/missions/histories'].get").exists())
    }

    private fun createManual(token: String) {
        mockMvc.perform(
            post("/api/missions/manual")
                .header(AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"category":"MEAL","text":"텀블러 사용하기"}"""),
        ).andExpect(status().isCreated)
    }

    private fun guestToken(): String {
        val body = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
    }
}
