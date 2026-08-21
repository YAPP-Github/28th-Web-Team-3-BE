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
    fun `mission knowledge seed matches the refreshed dataset`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM mission_knowledge").use { result ->
                    result.next()
                    assertEquals(29, result.getInt(1))
                }
                statement.executeQuery(
                    "SELECT category, item_code, content FROM mission_knowledge",
                ).use { result ->
                    val knowledge = buildSet {
                        while (result.next()) {
                            add("${result.getString("category")}|${result.getString("item_code")}|${result.getString("content")}")
                        }
                    }

                    assertEquals(expectedMissionKnowledge(), knowledge)
                    assertEquals(false, knowledge.any { it.contains("|SNACK|") })
                }
            }
        }
    }

    @Test
    fun `three active knowledge candidates are verified then recorded as one selection`() {
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
                    assertEquals(3, result.getInt("candidate_count"))
                    assertEquals(3, result.getInt("verified_count"))
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

    private fun expectedMissionKnowledge(): Set<String> = setOf(
        "MEAL|DELIVERY_FOOD|배달 메뉴 대신 집에서 직접 만드는 레시피 찾아보기",
        "MEAL|DINING_OUT|밀프랩으로 식단 미리 만들어 두기",
        "MEAL|DINING_OUT|저렴하게 장보기 스킬로 식비 아끼기",
        "MEAL|CAFE|카페별 할인된 기프티콘 미리 챙기기",
        "MEAL|CONVENIENCE_STORE|GS25 편의점 8월 행사 카카오페이·토스 할인 혜택 챙기기",
        "MEAL|CONVENIENCE_STORE|편의점 페이백 이벤트 혜택 챙기기",
        "MEAL|CONVENIENCE_STORE|편의점 할인 카드 혜택으로 절약하기",
        "MEAL|CONVENIENCE_STORE|GS25 우리동네GS클럽 구독: 한 끼 구독(월 3,990원)은 도시락·김밥·햄버거·빵·컵라면 등 간편식 20% 할인(1일 5회, 월 최대 15회). 카페 구독(월 2,500원)은 CAFE25 원두커피 전 품목 최대 25% 할인(1일 최대 10잔).",
        "MEAL|CONVENIENCE_STORE|CU 포켓CU 구독: 품목별 구독(월 1천~4천원대)으로 도시락·GET 커피·삼각김밥·컵라면 등 자주 먹는 카테고리를 지정해 20~30% 할인(매일 1~5회 한도).",
        "LIVING|CLOTHING|중고 거래로 안 입는 옷 판매해 현금화하기",
        "LIVING|CLOTHING|고가 브랜드 의류는 위탁판매하고, 중가 의류는 중고 거래로 처분하며, 저가 의류는 리클로 일괄처분하기",
        "LIVING|CLOTHING|무신사 등급 쿠폰·시즌 세일을 활용하고 아울렛과 중고 의류도 함께 비교하기",
        "LIVING|COSMETICS|다이소 기초템(토너·앰플) 활용하기",
        "LIVING|COSMETICS|리워딩스로 올리브영 리뷰 체험단 참여하기",
        "LIVING|COSMETICS|아모레퍼시픽(이니스프리·에뛰드·아리따움·헤라 등)은 다 쓴 공병을 매장에 가져가 뷰티포인트로 적립하고, LG생활건강(더페이스샵·빌리프 등)은 특정 브랜드 매장에서 공병 반납 시 포인트 적립 또는 할인 쿠폰을 받으며, 러쉬는 블랙 팟 5개를 모아 프레쉬 마스크 받기",
        "LIVING|COSMETICS|다이소 화장품 이용해 보기",
        "LIVING|HOUSEHOLD_GOODS|다이소에서 사면 손해인 품목은 피하고 정기배송과 매장픽업 활용하기",
        "LIVING|HOUSEHOLD_GOODS|수납용품 구매 없이 다이소템으로 수납 정리하기",
        "LIVING|HOUSEHOLD_GOODS|폴센트 앱으로 쿠팡 가격 변동을 추적하고 최저가 타이밍에 구매해 절약하기",
        "LIVING|BEAUTY|속눈썹펌 모델·헤어 모델·네일 모델, 모델나라·미몽·블로그 체험단 활용하기",
        "LIVING|BEAUTY|헤어모델 협찬 활용하기",
        "LIVING|BEAUTY|앞머리 셀프컷과 다이소 염색도구로 셀프미용 도전하기",
        "LIVING|BEAUTY|레뷰 등 체험단 플랫폼에서 뷰티·미용 시술 무료체험 신청하기",
        "HOBBY|CLASS|주민자치센터 문화·교양 강좌 활용하기",
        "HOBBY|PERFORMANCE_TICKET|영화·전시회·공연을 자주 다니는 사람을 위한 여가 생활 할인 카드 이용하기",
        "HOBBY|PERFORMANCE_TICKET|문체부 비수도권 공연 관람료 할인권 활용하기",
        "HOBBY|PERFORMANCE_TICKET|예술의전당 당일예매 시 특정 연령대 당일 할인티켓 활용하기(환불 불가)",
        "HOBBY|PERFORMANCE_TICKET|둘째·마지막 수요일 문화가 있는 날 1만원 관람과 정부 6천원 할인권을 중첩해 4천원 관람 활용하기",
        "HOBBY|PERFORMANCE_TICKET|영화 조조할인과 통신사 멤버십(SKT 주 1회, KT·LGU+ 월 1회 3천원)을 중복 활용하기",
    )

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
