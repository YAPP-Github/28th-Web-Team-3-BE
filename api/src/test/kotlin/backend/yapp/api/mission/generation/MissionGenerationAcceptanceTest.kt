package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import backend.yapp.core.mission.generation.service.MissionGenerationDeliveryTransactions
import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.sql.Statement
import java.time.Instant
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissionGenerationAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dataSource: DataSource,
    @Autowired private val missionGenerationExecutor: MissionGenerationExecutor,
    @Autowired private val deliveryTransactions: MissionGenerationDeliveryTransactions,
) {
    @Test
    fun `dispatcher atomically claims a due outbox row`() {
        val token = issueReadyGuest()
        val jobId = requestJob(token)

        val claimed = deliveryTransactions.claimDue()

        assertTrue(claimed.any { it.jobId == UUID.fromString(jobId) })
    }

    @Test
    fun `generation flow returns drafts and confirms selected missions idempotently`() {
        val token = issueReadyGuest()
        val jobId = requestJob(token)
        missionGenerationExecutor.execute(UUID.fromString(jobId))
        awaitSucceeded(token, jobId)

        val draftJson = mockMvc.perform(
            get("$GENERATION_PATH/$jobId/drafts")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(1))
            .andExpect(jsonPath("$.categories[0].category").value("MEAL"))
            .andExpect(jsonPath("$.categories[0].drafts.length()").value(4))
            .andExpect(jsonPath("$.categories[0].drafts[0].savingsLabel").isNotEmpty)
            .andReturn().response.contentAsString

        val draftIds: List<String> = JsonPath.read(
            draftJson,
            "$.categories[0].drafts[*].id",
        )
        val request = """{"selectedDraftIds":["${draftIds[0]}","${draftIds[1]}"]}"""
        val first = confirm(token, jobId, request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missions.length()").value(2))
            .andExpect(jsonPath("$.missions[0].status").value("ACTIVE"))
            .andReturn().response.contentAsString
        val second = confirm(token, jobId, request)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertEquals(
            JsonPath.read<String>(first, "$.missions[0].id"),
            JsonPath.read<String>(second, "$.missions[0].id"),
        )

        confirm(token, jobId, """{"selectedDraftIds":["${draftIds[2]}"]}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("MISSION_CONFIRM_CONFLICT"))
    }

    @Test
    fun `generation confirms more than four drafts across categories`() {
        val token = issueReadyGuest()
        val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
        insertTransportSurveyAnswer(guestUserId)
        val jobId = requestJob(token)
        missionGenerationExecutor.execute(UUID.fromString(jobId))
        awaitSucceeded(token, jobId)

        val draftsJson = mockMvc.perform(
            get("$GENERATION_PATH/$jobId/drafts")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(2))
            .andExpect(jsonPath("$.categories[0].drafts.length()").value(4))
            .andExpect(jsonPath("$.categories[1].drafts.length()").value(4))
            .andReturn().response.contentAsString
        val draftIds: List<String> = JsonPath.read(draftsJson, "$.categories[*].drafts[*].id")
        val request = draftIds.take(5).joinToString(",") { "\"$it\"" }

        confirm(token, jobId, "{\"selectedDraftIds\":[$request]}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missions.length()").value(5))
    }

    @Test
    fun `generation validates prerequisites ownership and selection size`() {
        val incompleteToken = issueGuestToken()
        mockMvc.perform(
            post(GENERATION_PATH)
                .header("Authorization", "Bearer $incompleteToken"),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_INCOMPLETE"))

        val ownerToken = issueReadyGuest()
        val otherToken = issueReadyGuest()
        val jobId = requestJob(ownerToken)
        missionGenerationExecutor.execute(UUID.fromString(jobId))
        awaitSucceeded(ownerToken, jobId)

        mockMvc.perform(
            get("$GENERATION_PATH/$jobId")
                .header("Authorization", "Bearer $otherToken"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("MISSION_GENERATION_JOB_NOT_FOUND"))

        val draftsJson = mockMvc.perform(
            get("$GENERATION_PATH/$jobId/drafts")
                .header("Authorization", "Bearer $ownerToken"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val draftId = JsonPath.read<String>(draftsJson, "$.categories[0].drafts[0].id")

        confirm(ownerToken, jobId, """{"selectedDraftIds":[]}""")
            .andExpect(status().isBadRequest)
        confirm(ownerToken, jobId, """{"selectedDraftIds":["$draftId","$draftId"]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_CONFIRM_INVALID"))
    }

    @Test
    fun `generation endpoints require authentication and are published in OpenAPI`() {
        mockMvc.perform(post(GENERATION_PATH)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("$GENERATION_PATH/${UUID.randomUUID()}")).andExpect(status().isUnauthorized)

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['$GENERATION_PATH'].post.responses['202']").exists())
            .andExpect(jsonPath("$.paths['$GENERATION_PATH/{jobId}'].get").exists())
            .andExpect(jsonPath("$.paths['$GENERATION_PATH/{jobId}/drafts'].get").exists())
            .andExpect(jsonPath("$.paths['$GENERATION_PATH/{jobId}/confirm'].post").exists())
    }

    private fun requestJob(token: String): String {
        val response = mockMvc.perform(
            post(GENERATION_PATH)
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").isNotEmpty)
            .andReturn().response.contentAsString
        return JsonPath.read(response, "$.jobId")
    }

    private fun awaitSucceeded(token: String, jobId: String) {
        repeat(100) {
            val response = mockMvc.perform(
                get("$GENERATION_PATH/$jobId")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk).andReturn().response.contentAsString
            when (val currentStatus = JsonPath.read<String>(response, "$.status")) {
                "SUCCEEDED" -> {
                    assertEquals("MOCK", JsonPath.read<String>(response, "$.generationSource"))
                    return
                }
                "FAILED" -> error("Mission generation failed")
                else -> {
                    assertTrue(currentStatus == "PENDING" || currentStatus == "RUNNING")
                    Thread.sleep(20)
                }
            }
        }
        error("Mission generation did not finish")
    }

    private fun confirm(token: String, jobId: String, body: String) =
        mockMvc.perform(
            post("$GENERATION_PATH/$jobId/confirm")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun issueReadyGuest(): String =
        issueGuestToken().also { token ->
            val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
            insertCompletedOnboarding(guestUserId)
            insertMealSurvey(guestUserId)
        }

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
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

    private fun insertTransportSurveyAnswer(guestUserId: Long) {
        dataSource.connection.use { connection ->
            val surveyId = connection.prepareStatement(
                "SELECT id FROM mission_survey WHERE guest_user_id = ?",
            ).use { statement ->
                statement.setLong(1, guestUserId)
                statement.executeQuery().use { result ->
                    check(result.next())
                    result.getLong(1)
                }
            }
            connection.prepareStatement(
                """
                    INSERT INTO mission_survey_answer
                        (mission_survey_id, category_code, question_code, value_type, answer_code)
                    VALUES (?, 'TRANSPORT', 'TRANSPORT_TARGET', 'OPTION', 'TAXI')
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, surveyId)
                statement.executeUpdate()
            }
        }
    }

    companion object {
        private const val GENERATION_PATH = "/api/missions/generation-jobs"
    }
}
