package backend.yapp.api.mission.lifecycle

import backend.yapp.core.mission.generation.service.MissionLifecycleService
import com.jayway.jsonpath.JsonPath
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
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
class MissionLifecycleAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dataSource: DataSource,
    @Autowired private val lifecycleService: MissionLifecycleService,
) {
    @Test
    fun `manual mission is isolated by owner and completion is idempotent`() {
        val owner = guestToken()
        val other = guestToken()
        val created = createManual(owner)
        val id = JsonPath.read<String>(created, "$.id")
        val weekEndsAt = Instant.parse(JsonPath.read(created, "$.weekEndsAt"))
            .atZone(ZoneId.of("Asia/Seoul"))
        assertEquals(DayOfWeek.MONDAY, weekEndsAt.dayOfWeek)
        assertEquals(0, weekEndsAt.hour)

        mockMvc.perform(get("/api/missions").header(AUTHORIZATION, "Bearer $owner"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missions[0].source").value("MANUAL"))
            .andExpect(jsonPath("$.missions[0].savingsLabel").value("예상 절약액 미산정"))

        mockMvc.perform(get("/api/missions").header(AUTHORIZATION, "Bearer $other"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missions.length()").value(0))

        repeat(2) {
            mockMvc.perform(
                patch("/api/missions/MANUAL/$id/complete")
                    .header(AUTHORIZATION, "Bearer $owner"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
        }
    }

    @Test
    fun `overdue active mission becomes incomplete and cannot be completed`() {
        val token = guestToken()
        val id = JsonPath.read<String>(createManual(token), "$.id")
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE manual_mission SET week_ends_at = ? WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, Instant.parse("2020-01-01T00:00:00Z"))
                statement.setObject(2, UUID.fromString(id))
                assertEquals(1, statement.executeUpdate())
            }
        }

        lifecycleService.markOverdueIncomplete()

        mockMvc.perform(
            patch("/api/missions/MANUAL/$id/complete")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("MISSION_STATUS_CONFLICT"))
    }

    @Test
    fun `lifecycle endpoints are published in OpenAPI`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/missions'].get").exists())
            .andExpect(jsonPath("$.paths['/api/missions/manual'].post").exists())
            .andExpect(jsonPath("$.paths['/api/missions/{source}/{missionId}/complete'].patch").exists())
    }

    private fun createManual(token: String): String =
        mockMvc.perform(
            post("/api/missions/manual")
                .header(AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category": "MEAL",
                      "text": "이번 주 배달 대신 집밥 먹기",
                      "targetCount": 2,
                      "targetUnit": "TIMES_PER_WEEK"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn().response.contentAsString

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
