package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    @Autowired private val executor: MissionGenerationExecutor,
) {
    @Test
    fun `one item request creates deterministic candidates and same item can be generated again`() {
        val token = readyGuestToken()
        val firstJobId = requestJob(token)
        executor.execute(UUID.fromString(firstJobId), 1)

        val draftsJson = mockMvc.perform(
            get("$GENERATION_PATH/$firstJobId/drafts").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(1))
            .andExpect(jsonPath("$.categories[0].category").value("MEAL"))
            .andExpect(jsonPath("$.categories[0].drafts.length()").value(3))
            .andExpect(jsonPath("$.categories[0].drafts[0].item").value("DELIVERY_FOOD"))
            .andExpect(jsonPath("$.categories[0].drafts[0].targetCount").value(2))
            .andExpect(jsonPath("$.categories[0].drafts[0].estimatedSavingsWon").value(20_000))
            .andExpect(jsonPath("$.categories[0].drafts[2].targetCount").value(1))
            .andExpect(jsonPath("$.categories[0].drafts[2].estimatedSavingsWon").value(10_000))
            .andExpect(jsonPath("$.categories[0].drafts[0].savingsDisclaimer").isNotEmpty)
            .andReturn().response.contentAsString
        val draftIds: List<String> = JsonPath.read(draftsJson, "$.categories[0].drafts[*].id")

        mockMvc.perform(
            post("$GENERATION_PATH/$firstJobId/confirm")
                .header(AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"selectedDraftIds":["${draftIds[0]}","${draftIds[2]}"]}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.missions.length()").value(2))

        val secondJobId = requestJob(token)
        assertNotEquals(firstJobId, secondJobId)
    }

    @Test
    fun `generation validates input and requires completed onboarding`() {
        val incomplete = guestToken()
        request(incomplete, VALID_BODY).andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_INCOMPLETE"))

        val ready = readyGuestToken()
        request(
            ready,
            """{"category":"MEAL","item":"GAME","baselineFrequency":5,"baselineAmountWon":50000}""",
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_GENERATION_INPUT_INVALID"))
        request(
            ready,
            """{"category":"MEAL","item":"DELIVERY_FOOD","baselineFrequency":0,"baselineAmountWon":50000}""",
        ).andExpect(status().isBadRequest)
        request(
            ready,
            """{"category":"LIVING","item":"SELF_DEVELOPMENT","baselineFrequency":5,"baselineAmountWon":50000}""",
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("MISSION_GENERATION_INPUT_INVALID"))
    }

    @Test
    fun `old survey is unavailable and new generation contract is published`() {
        val token = guestToken()
        mockMvc.perform(
            get("/api/missions/surveys/questions").header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isMethodNotAllowed)
        mockMvc.perform(post(GENERATION_PATH)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['$GENERATION_PATH'].post").exists())
            .andExpect(jsonPath("$.components.schemas.MissionGenerationCreateRequest.properties.item").exists())
            .andExpect(jsonPath("$.paths['/api/missions/surveys']").doesNotExist())
    }

    @Test
    fun `mission knowledge seed preserves slash-delimited rows`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM mission_knowledge").use { result ->
                    result.next()
                    assertEquals(27, result.getInt(1))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) FROM mission_knowledge WHERE item_code = 'HOUSEHOLD_GOODS'",
                ).use { result ->
                    result.next()
                    assertEquals(6, result.getInt(1))
                }
            }
        }
    }

    @Test
    fun `six active knowledge candidates are verified then recorded as one selection`() {
        val token = readyGuestToken()
        val response = request(
            token,
            """{"category":"LIVING","item":"HOUSEHOLD_GOODS","baselineFrequency":3,"baselineAmountWon":30000}""",
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        val jobId = JsonPath.read<String>(response, "$.jobId")

        executor.execute(UUID.fromString(jobId), 1)

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT candidate_count, verified_count, selected_knowledge_ids, selection_policy
                    FROM mission_knowledge_retrieval_trace
                    WHERE job_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(jobId))
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(6, result.getInt("candidate_count"))
                    assertEquals(6, result.getInt("verified_count"))
                    assertEquals(1, result.getString("selected_knowledge_ids").split(",").size)
                    assertEquals("DETERMINISTIC_RANDOM_1", result.getString("selection_policy"))
                }
            }
        }
    }

    private fun requestJob(token: String): String {
        val body = request(token, VALID_BODY).andExpect(status().isAccepted)
            .andReturn().response.contentAsString
        return JsonPath.read(body, "$.jobId")
    }

    private fun request(token: String, body: String) = mockMvc.perform(
        post(GENERATION_PATH)
            .header(AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun readyGuestToken(): String = guestToken().also { token ->
        val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
        val now = Instant.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO onboarding_profile
                    (guest_user_id, birth_date, address, monthly_salary_manwon, monthly_saving_manwon,
                     net_worth_manwon, goal_period_months, status, created_at, updated_at)
                VALUES (?, DATE '1998-03-01', 'SEOUL', 350, 100, 1800, 24, 'COMPLETED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, guestUserId)
                statement.setObject(2, now)
                statement.setObject(3, now)
                statement.executeUpdate()
            }
        }
    }

    private fun guestToken(): String {
        val body = mockMvc.perform(
            post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    companion object {
        private const val GENERATION_PATH = "/api/missions/generation-jobs"
        private const val AUTHORIZATION = "Authorization"
        private const val VALID_BODY =
            """{"category":"MEAL","item":"DELIVERY_FOOD","baselineFrequency":5,"baselineAmountWon":50000}"""
    }
}
