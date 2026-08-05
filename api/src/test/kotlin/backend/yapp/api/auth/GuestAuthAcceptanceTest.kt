package backend.yapp.api.auth

import backend.yapp.core.auth.fixture.GuestAuthFixture
import backend.yapp.core.auth.port.AuthTokenPort
import backend.yapp.core.auth.service.GuestWithdrawalService
import org.hamcrest.Matchers.not
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.sql.DataSource
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestAuthAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenPort: AuthTokenPort,
    @Autowired private val dataSource: DataSource,
    @Autowired private val withdrawalService: GuestWithdrawalService,
) {
    @Test
    fun `same UUID maps to the same guest subject`() {
        val first = issue(GuestAuthFixture.IDENTIFIER)
        val second = issue(GuestAuthFixture.IDENTIFIER)

        assertThat(tokenPort.parseAccessToken(first).guestUserId)
            .isEqualTo(tokenPort.parseAccessToken(second).guestUserId)
    }

    @Test
    fun `refresh rotates token and rejects replay`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        val rotated = mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken", not(refreshToken)))
            .andReturn().response.contentAsString

        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isUnauthorized)

        assertThat(rotated).isNotBlank()
    }

    @Test
    fun `refresh token cannot authenticate bearer request`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        mockMvc.perform(
            get("/api/unknown")
                .header("Authorization", "Bearer $refreshToken"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `only one simultaneous refresh can consume the same token`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val statuses = executor.invokeAll(List(2) {
                Callable {
                    mockMvc.perform(
                        post("/api/auth/guest/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"$refreshToken\"}"),
                    ).andReturn().response.status
                }
            }).map { it.get() }
            assertThat(statuses).containsExactlyInAnyOrder(200, 401)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `simultaneous first requests for the same UUID both succeed`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val statuses = executor.invokeAll(List(2) {
                Callable {
                    mockMvc.perform(
                        post("/api/auth/guest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"uuid\":\"c4ffb5fe-9fb5-4f9d-aa26-9c31c66cc4f1\"}"),
                    ).andReturn().response.status
                }
            }).map { it.get() }
            assertThat(statuses).containsOnly(201)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `invalid identifier is rejected`() {
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"not-a-uuid\"}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `access token cannot be used to refresh`() {
        val accessToken = issue(GuestAuthFixture.IDENTIFIER)
        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$accessToken\"}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `expired access token header does not block refresh endpoint`() {
        val refreshToken = refreshTokenOf(issueWithResponse(GuestAuthFixture.IDENTIFIER).response.contentAsString)

        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .header("Authorization", "Bearer ${expiredAccessToken()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `missing request value is rejected as bad request`() {
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `expired or forged refresh token is rejected with security JSON`() {
        listOf(expiredRefreshToken(), forgedRefreshToken()).forEach { token ->
            mockMvc.perform(
                post("/api/auth/guest/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"$token\"}"),
            ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.name").value("UNAUTHORIZED"))
        }
    }

    @Test
    fun `OpenAPI document publishes guest authentication contract`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auth/guest'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/guest/refresh'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/guest'].delete.responses['204']").exists())
    }

    @Test
    fun `authenticated guest withdrawal deletes account invalidates tokens and permits same UUID rejoin`() {
        val identifier = "d6e055ee-ebfd-4a4d-9aa8-2b268fd58ae4"
        val issued = issueWithResponse(identifier).response.contentAsString
        val accessToken: String = JsonPath.read(issued, "$.accessToken")
        val refreshToken = refreshTokenOf(issued)
        val guestUserId = tokenPort.parseAccessToken(accessToken).guestUserId

        mockMvc.perform(delete("/api/auth/guest").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isNoContent)

        mockMvc.perform(delete("/api/auth/guest").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/auth/guest/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"$refreshToken\"}"),
        ).andExpect(status().isUnauthorized)

        val rejoined = issue(identifier)
        assertThat(tokenPort.parseAccessToken(rejoined).guestUserId).isNotEqualTo(guestUserId)
    }

    @Test
    fun `guest withdrawal requires access token authentication`() {
        mockMvc.perform(delete("/api/auth/guest"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `withdrawal hard deletes every user-owned row without affecting another guest`() {
        val issued = issueWithResponse(UUID.randomUUID().toString()).response.contentAsString
        val accessToken: String = JsonPath.read(issued, "$.accessToken")
        val guestUserId = tokenPort.parseAccessToken(accessToken).guestUserId
        val otherAccessToken = issue(UUID.randomUUID().toString())
        val otherGuestUserId = tokenPort.parseAccessToken(otherAccessToken).guestUserId
        insertAllUserOwnedRows(guestUserId)

        mockMvc.perform(delete("/api/auth/guest").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isNoContent)

        userOwnedTableNames.forEach { table ->
            assertThat(rowCount(table, guestUserId)).describedAs(table).isZero()
        }
        assertThat(relatedRowCount("mission_draft", guestUserId)).isZero()
        assertThat(relatedRowCount("mission_recommendation_candidate", guestUserId)).isZero()
        assertThat(rowCount("guest_user", otherGuestUserId)).isOne()
    }

    @Test
    fun `withdrawal rolls back all previous deletes when account deletion is blocked`() {
        val accessToken = issue(UUID.randomUUID().toString())
        val guestUserId = tokenPort.parseAccessToken(accessToken).guestUserId
        executeSql(
            """
            CREATE TABLE IF NOT EXISTS withdrawal_guard (
                guest_user_id BIGINT NOT NULL REFERENCES guest_user (id)
            );
            DELETE FROM withdrawal_guard;
            INSERT INTO withdrawal_guard (guest_user_id) VALUES ($guestUserId)
            """.trimIndent(),
        )

        assertThatThrownBy { withdrawalService.withdraw(guestUserId) }.isNotNull()

        assertThat(rowCount("guest_user", guestUserId)).isOne()
        assertThat(rowCount("refresh_token", guestUserId)).isOne()
    }

    private fun issue(identifier: String): String =
        JsonPath.read(issueWithResponse(identifier).response.contentAsString, "$.accessToken")

    private fun refreshTokenOf(responseJson: String): String = JsonPath.read(responseJson, "$.refreshToken")

    private fun issueWithResponse(identifier: String) =
        mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"$identifier\"}"),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

    private fun insertAllUserOwnedRows(guestUserId: Long) {
        val jobId = UUID.randomUUID()
        val draftId = UUID.randomUUID()
        val missionId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        val templateId = firstTemplateId()
        executeSql(
            """
            INSERT INTO onboarding_profile (guest_user_id, status, created_at, updated_at)
            VALUES ($guestUserId, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
            INSERT INTO onboarding_goal (guest_user_id, plan, period_months, monthly_saving_manwon, uplift_permille, target_amount_manwon, config_version, created_at)
            VALUES ($guestUserId, 'PLAN_1', 12, 10, 10, 120, 'V1', CURRENT_TIMESTAMP);
            INSERT INTO goal (guest_user_id, target_amount_manwon, period_months, monthly_target_manwon, base_amount_manwon, started_at, created_at, updated_at, version)
            VALUES ($guestUserId, 120, 12, 10, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
            INSERT INTO monthly_saving (guest_user_id, year_month, saved_amount_manwon, updated_at)
            VALUES ($guestUserId, '2026-08', 10, CURRENT_TIMESTAMP);
            INSERT INTO mission_survey (guest_user_id, schema_version, created_at, updated_at, version)
            VALUES ($guestUserId, 'V1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
            INSERT INTO mission_survey_answer (mission_survey_id, category_code, question_code, value_type, answer_code)
            SELECT id, 'MEAL', 'MEAL_TARGET', 'OPTION', 'DELIVERY' FROM mission_survey WHERE guest_user_id = $guestUserId;
            INSERT INTO mission_generation_job (id, guest_user_id, status, active_generation_key, generation_source, expires_at, created_at, updated_at, version)
            VALUES ('$jobId', $guestUserId, 'SUCCEEDED', NULL, 'MOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
            INSERT INTO mission_draft (id, job_id, template_id, category, title, description, action_code, metric_type, target_count, target_unit, estimated_savings_won, created_at)
            VALUES ('$draftId', '$jobId', $templateId, 'MEAL', 'title', 'description', 'ACTION', 'COUNT', 1, 'TIMES_PER_WEEK', 1000, CURRENT_TIMESTAMP);
            INSERT INTO mission (id, job_id, draft_id, guest_user_id, category, title, description, action_code, metric_type, target_count, target_unit, estimated_savings_won, status, created_at, week_ends_at)
            VALUES ('$missionId', '$jobId', '$draftId', $guestUserId, 'MEAL', 'title', 'description', 'ACTION', 'COUNT', 1, 'TIMES_PER_WEEK', 1000, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
            INSERT INTO manual_mission (id, guest_user_id, category, mission_text, structured_tags, target_count, target_unit, status, week_ends_at, created_at)
            VALUES ('${UUID.randomUUID()}', $guestUserId, 'MEAL', 'text', '[]', 1, 'TIMES_PER_WEEK', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
            INSERT INTO mission_outcome_event (id, guest_user_id, mission_source, mission_id, final_status, occurred_at)
            VALUES ('${UUID.randomUUID()}', $guestUserId, 'RECOMMENDED', '$missionId', 'COMPLETED', CURRENT_TIMESTAMP);
            INSERT INTO mission_recommendation_snapshot (id, guest_user_id, algorithm_version, semantic_provider, semantic_model_version, eligible_candidate_ids, retrieved_candidate_ids, weekly_context_snapshot, created_at)
            VALUES ('$snapshotId', $guestUserId, 'V1', 'TEST', 'V1', '[]', '[]', '{}', CURRENT_TIMESTAMP);
            INSERT INTO mission_recommendation_candidate (id, snapshot_id, template_id, rank_position, raw_score, adjusted_score, retrieved, exploration_applied, applied_penalties, shown)
            VALUES ('${UUID.randomUUID()}', '$snapshotId', $templateId, 1, 1.0, 1.0, TRUE, FALSE, '[]', FALSE)
            """.trimIndent(),
        )
    }

    private fun firstTemplateId(): Long =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT MIN(id) FROM mission_draft_template").use { result ->
                    check(result.next())
                    result.getLong(1)
                }
            }
        }

    private fun executeSql(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                sql.split(";\n").filter(String::isNotBlank).forEach(statement::executeUpdate)
            }
        }
    }

    private fun rowCount(table: String, guestUserId: Long): Long {
        val userIdColumn = if (table == "guest_user") "id" else "guest_user_id"
        return dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $table WHERE $userIdColumn = ?").use { statement ->
                statement.setLong(1, guestUserId)
                statement.executeQuery().use { result ->
                    check(result.next())
                    result.getLong(1)
                }
            }
        }
    }

    private fun relatedRowCount(table: String, guestUserId: Long): Long {
        val query = when (table) {
            "mission_draft" -> """
                SELECT COUNT(*) FROM mission_draft draft
                JOIN mission_generation_job job ON draft.job_id = job.id
                WHERE job.guest_user_id = ?
            """.trimIndent()
            "mission_recommendation_candidate" -> """
                SELECT COUNT(*) FROM mission_recommendation_candidate candidate
                JOIN mission_recommendation_snapshot snapshot ON candidate.snapshot_id = snapshot.id
                WHERE snapshot.guest_user_id = ?
            """.trimIndent()
            else -> error("Unsupported related table: $table")
        }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setLong(1, guestUserId)
                statement.executeQuery().use { result ->
                    check(result.next())
                    result.getLong(1)
                }
            }
        }
    }

    private fun expiredRefreshToken(): String = signedToken(
        secret = "test-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().minusSeconds(60),
        type = "refresh",
    )

    private fun forgedRefreshToken(): String = signedToken(
        secret = "forged-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().plusSeconds(3600),
        type = "refresh",
    )

    private fun expiredAccessToken(): String = signedToken(
        secret = "test-secret-key-that-is-at-least-32-bytes",
        expiresAt = Instant.now().minusSeconds(60),
        type = "access",
    )

    private fun signedToken(secret: String, expiresAt: Instant, type: String): String {
        val now = Instant.now().minusSeconds(120)
        val claims = JWTClaimsSet.Builder()
            .subject("1").issuer("yapp-test").audience("yapp-client")
            .issueTime(Date.from(now)).expirationTime(Date.from(expiresAt))
            .jwtID("8d1ec76a-9df0-43d0-89b8-aedec558dc23").claim("type", type).build()
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).also {
            it.sign(MACSigner(secret.toByteArray()))
        }.serialize()
    }

    companion object {
        private val userOwnedTableNames = listOf(
            "guest_user",
            "refresh_token",
            "onboarding_profile",
            "onboarding_goal",
            "goal",
            "monthly_saving",
            "mission_survey",
            "mission_generation_job",
            "mission",
            "manual_mission",
            "mission_outcome_event",
            "mission_recommendation_snapshot",
        )
    }
}
