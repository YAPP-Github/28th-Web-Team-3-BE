package backend.yapp.api.mission.generation

import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcome
import backend.yapp.core.mission.generation.port.MissionBlogSearchOutcomeCategory
import backend.yapp.core.mission.generation.port.MissionBlogSearchPort
import backend.yapp.core.mission.generation.port.MissionBlogSearchResult
import backend.yapp.core.mission.generation.service.MissionGenerationExecutor
import com.jayway.jsonpath.JsonPath
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:mission-blog-tip-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MissionGenerationBlogTipPersistenceAcceptanceTest.BlogSearchTestConfig::class)
class MissionGenerationBlogTipPersistenceAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dataSource: DataSource,
    @Autowired private val executor: MissionGenerationExecutor,
) {
    @Test
    fun `successful search result is persisted as a mission blog tip`() {
        val token = readyGuestToken()
        val guestUserId = SignedJWT.parse(token).jwtClaimsSet.subject.toLong()
        val jobId = requestJob(token)

        executor.execute(jobId)

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT title, source, url, item_code FROM mission_blog_tip WHERE guest_user_id = ?",
            ).use { statement ->
                statement.setLong(1, guestUserId)
                statement.executeQuery().use { resultSet ->
                    kotlin.test.assertEquals(true, resultSet.next())
                    kotlin.test.assertEquals("절약 팁", resultSet.getString("title"))
                    kotlin.test.assertEquals("작성자", resultSet.getString("source"))
                    kotlin.test.assertEquals("https://blog.example.test/saving-tip", resultSet.getString("url"))
                    kotlin.test.assertEquals("DELIVERY_FOOD", resultSet.getString("item_code"))
                    kotlin.test.assertEquals(false, resultSet.next())
                }
            }
        }
    }

    private fun requestJob(token: String): UUID {
        val response = mockMvc.perform(
            post("/api/missions/generation-jobs")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"category":"MEAL","item":"DELIVERY_FOOD","baselineFrequency":3,"baselineAmountWon":30000}""",
                ),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.jobId"))
    }

    private fun readyGuestToken(): String {
        val tokenResponse = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val token: String = JsonPath.read(tokenResponse, "$.accessToken")
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
        return token
    }

    @TestConfiguration
    class BlogSearchTestConfig {
        @Bean
        @Primary
        fun testMissionBlogSearchPort(): MissionBlogSearchPort = object : MissionBlogSearchPort {
            override fun search(query: String, count: Int): MissionBlogSearchOutcome =
                MissionBlogSearchOutcome.Completed(
                    category = MissionBlogSearchOutcomeCategory.SUCCESS,
                    providerItemCount = 1,
                    results = listOf(
                        MissionBlogSearchResult(
                            title = "절약 팁",
                            description = "설명",
                            source = "작성자",
                            url = "https://blog.example.test/saving-tip",
                        ),
                    ),
                )
        }
    }
}
