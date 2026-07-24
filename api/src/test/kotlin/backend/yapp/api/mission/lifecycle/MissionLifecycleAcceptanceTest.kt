package backend.yapp.api.mission.lifecycle

import backend.yapp.core.mission.generation.service.MissionLifecycleService
import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.sql.Statement
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
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
    @Autowired private val lifecycleService: MissionLifecycleService,
) {
    @Test
    fun `recommended mission deletion is owner scoped and preserves outcome history`() {
        val owner = issueReadyGuest()
        val other = issueReadyGuest()
        val missionId = createRecommended(owner)

        mockMvc.perform(
            delete("/api/missions/recommended/$missionId")
                .header(AUTHORIZATION, "Bearer $other"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_NOT_FOUND"))

        completeRecommended(owner, missionId)
        assertEquals(1, outcomeEventCount(UUID.fromString(missionId)))

        mockMvc.perform(
            delete("/api/missions/recommended/$missionId")
                .header(AUTHORIZATION, "Bearer $owner"),
        ).andExpect(status().isNoContent)
            .andExpect { result -> assertEquals("", result.response.contentAsString) }

        mockMvc.perform(get("/api/missions").header(AUTHORIZATION, "Bearer $owner"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missions.length()").value(0))
        assertEquals(1, outcomeEventCount(UUID.fromString(missionId)))
    }

    @Test
    fun `recommended mission deletion rejects missing manual and malformed identifiers`() {
        val token = guestToken()
        val manualId = JsonPath.read<String>(createManual(token), "$.id")

        mockMvc.perform(
            delete("/api/missions/recommended/${UUID.randomUUID()}")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_NOT_FOUND"))
        mockMvc.perform(
            delete("/api/missions/recommended/$manualId")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_NOT_FOUND"))
        mockMvc.perform(
            delete("/api/missions/recommended/not-a-uuid")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isBadRequest)
    }

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
            .andExpect(jsonPath("$.paths['/api/missions/recommended/{missionId}'].delete.responses['204']").exists())
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

    private fun issueReadyGuest(): String =
        guestToken().also { token ->
            val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
            insertCompletedOnboarding(guestUserId)
            insertMealSurvey(guestUserId)
        }

    private fun createRecommended(token: String): String {
        val jobId = JsonPath.read<String>(
            mockMvc.perform(post(GENERATION_PATH).header(AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isAccepted)
                .andReturn().response.contentAsString,
            "$.jobId",
        )
        repeat(100) {
            val response = mockMvc.perform(
                get("$GENERATION_PATH/$jobId").header(AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk).andReturn().response.contentAsString
            when (JsonPath.read<String>(response, "$.status")) {
                "SUCCEEDED" -> {
                    val drafts = mockMvc.perform(
                        get("$GENERATION_PATH/$jobId/drafts").header(AUTHORIZATION, "Bearer $token"),
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                    val draftId = JsonPath.read<String>(drafts, "$.categories[0].drafts[0].id")
                    val confirmed = mockMvc.perform(
                        post("$GENERATION_PATH/$jobId/confirm")
                            .header(AUTHORIZATION, "Bearer $token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"selectedDraftIds":["$draftId"]}"""),
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                    return JsonPath.read(confirmed, "$.missions[0].id")
                }
                "FAILED" -> error("Mission generation failed")
                else -> Thread.sleep(20)
            }
        }
        error("Mission generation did not finish")
    }

    private fun completeRecommended(token: String, missionId: String) {
        mockMvc.perform(
            patch("/api/missions/RECOMMENDED/$missionId/complete")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)
    }

    private fun outcomeEventCount(missionId: UUID): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM mission_outcome_event WHERE mission_id = ?").use { statement ->
                statement.setObject(1, missionId)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            }
        }

    private fun insertCompletedOnboarding(guestUserId: Long) {
        val now = Instant.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    INSERT INTO onboarding_profile
                        (guest_user_id, birth_date, monthly_salary_manwon, monthly_saving_manwon,
                         net_worth_manwon, goal_period_months, status, created_at, updated_at)
                    VALUES (?, DATE '1998-03-01', 350, 100, 1800, 24, 'COMPLETED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, guestUserId)
                statement.setObject(2, now)
                statement.setObject(3, now)
                statement.executeUpdate()
            }
        }
    }

    private fun insertMealSurvey(guestUserId: Long) {
        val now = Instant.now()
        dataSource.connection.use { connection ->
            val surveyId = connection.prepareStatement(
                """
                    INSERT INTO mission_survey
                        (guest_user_id, schema_version, created_at, updated_at, version)
                    VALUES (?, 'V1', ?, ?, 0)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setLong(1, guestUserId)
                statement.setObject(2, now)
                statement.setObject(3, now)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next())
                    keys.getLong(1)
                }
            }
            connection.prepareStatement(
                """
                    INSERT INTO mission_survey_answer
                        (mission_survey_id, category_code, question_code, value_type, answer_code)
                    VALUES (?, 'MEAL', 'MEAL_TARGET', 'OPTION', 'DELIVERY')
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, surveyId)
                statement.executeUpdate()
            }
        }
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val GENERATION_PATH = "/api/missions/generation-jobs"
    }
}
