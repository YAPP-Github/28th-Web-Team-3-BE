package backend.yapp.api.mission.lifecycle

import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
) {
    @Test
    fun `manual mission completion drives current weekly list and progress`() {
        val token = guestToken()
        val missionId = JsonPath.read<String>(createManual(token), "$.id")

        mockMvc.perform(get("/api/missions/progress").header(AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completedCount").value(0))
            .andExpect(jsonPath("$.totalCount").value(1))

        mockMvc.perform(
            patch("/api/missions/manual/$missionId/complete").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(
            get("/api/missions").param("status", "ACTIVE").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk).andExpect(jsonPath("$.missions.length()").value(0))
        mockMvc.perform(get("/api/missions/progress").header(AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completedCount").value(1))
            .andExpect(jsonPath("$.progressPercent").value(100))
    }

    @Test
    fun `user deletion is soft and keeps weekly completion history`() {
        val token = guestToken()
        val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
        val missionId = JsonPath.read<String>(createManual(token), "$.id")
        mockMvc.perform(
            patch("/api/missions/MANUAL/$missionId/complete").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/api/missions/manual/$missionId").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/missions").header(AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.missions.length()").value(0))

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT deleted_at FROM manual_mission WHERE id = ? AND guest_user_id = ?",
            ).use { statement ->
                statement.setObject(1, UUID.fromString(missionId))
                statement.setLong(2, guestUserId)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getObject(1) != null)
                }
            }
            connection.prepareStatement(
                "SELECT COUNT(*) FROM mission_weekly_completion WHERE mission_id = ?",
            ).use { statement ->
                statement.setObject(1, UUID.fromString(missionId))
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    @Test
    fun `mission source path accepts both cases and rejects unknown values`() {
        val token = guestToken()
        val missionId = JsonPath.read<String>(createManual(token), "$.id")

        mockMvc.perform(
            delete("/api/missions/MANUAL/$missionId").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            delete("/api/missions/recommended/${UUID.randomUUID()}")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_NOT_FOUND"))

        mockMvc.perform(
            delete("/api/missions/unknown/${UUID.randomUUID()}")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
    }

    @Test
    fun `catalog and lifecycle endpoints publish the replacement policy`() {
        val token = guestToken()
        mockMvc.perform(get("/api/missions/catalog").header(AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(3))
            .andExpect(jsonPath("$.categories[0].items.length()").value(6))
            .andExpect(jsonPath("$.categories[1].items.length()").value(4))
            .andExpect(jsonPath("$.categories[2].items.length()").value(3))
            .andExpect(jsonPath("$.categories[1].items[?(@.code == 'SELF_DEVELOPMENT')]").isEmpty)
            .andExpect(jsonPath("$.categories[2].items[?(@.code == 'DIGITAL_CONTENT')]").isEmpty)

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/missions/catalog'].get").exists())
            .andExpect(jsonPath("$.paths['/api/missions/progress'].get").exists())
            .andExpect(jsonPath("$.paths['/api/missions/{source}/{missionId}'].delete").exists())
            .andExpect(jsonPath("$.paths['/api/missions/{source}/{missionId}/complete'].patch").exists())
    }

    private fun createManual(token: String): String = mockMvc.perform(
        post("/api/missions/manual")
            .header(AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"category":"MEAL","text":"탄산음료 3번 이하로 마시기"}"""),
    ).andExpect(status().isCreated)
        .andExpect(jsonPath("$.targetCount").doesNotExist())
        .andExpect(jsonPath("$.estimatedSavingsWon").doesNotExist())
        .andReturn().response.contentAsString

    private fun guestToken(): String {
        val body = mockMvc.perform(
            post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
    }
}
