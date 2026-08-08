package backend.yapp.api.policy

import backend.yapp.core.policy.port.ExternalYouthPolicy
import backend.yapp.core.policy.port.ExternalYouthPolicyPage
import backend.yapp.core.policy.port.YouthPolicyProviderPort
import backend.yapp.core.policy.service.PolicySyncService
import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BenefitAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val policySyncService: PolicySyncService,
) {
    @TestConfiguration
    class FakeProviderConfig {
        @Bean
        @Primary
        fun fakeProvider(): YouthPolicyProviderPort = object : YouthPolicyProviderPort {
            override fun fetch(pageNum: Int, pageSize: Int): ExternalYouthPolicyPage {
                if (pageNum > 1) return ExternalYouthPolicyPage(emptyList(), 3)
                return ExternalYouthPolicyPage(
                    policies = listOf(
                        // 포함(주거·전월세, 기간 미상) → 저장
                        ExternalYouthPolicy(
                            externalId = "OPEN1", title = "청년 월세 지원",
                            largeCategory = "주거", mediumCategory = "전월세 및 주거급여 지원",
                            description = "청년 월세를 지원합니다.",
                        ),
                        // 스코프 밖(창업) → 미저장
                        ExternalYouthPolicy(externalId = "STARTUP1", title = "청년 창업 지원", mediumCategory = "창업"),
                        // 마감(과거 신청기간) → 미저장
                        ExternalYouthPolicy(
                            externalId = "EXPIRED1", title = "지난 건강 지원",
                            mediumCategory = "건강", applyPeriodText = "20200101 ~ 20200131",
                        ),
                    ),
                    totalCount = 3,
                )
            }
        }
    }

    @BeforeEach
    fun sync() {
        policySyncService.sync()
    }

    @Test
    fun `sync stores only in-scope open policies and serves them`() {
        val token = issueGuestToken()

        // 스코프 밖·마감 제외되고 OPEN1만 저장
        val listJson = mockMvc.perform(get("/api/policies").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("청년 월세 지원"))
            .andExpect(jsonPath("$[0].largeCategory").value("주거"))
            .andExpect(jsonPath("$[0].bookmarked").value(false))
            .andReturn().response.contentAsString
        val id = JsonPath.read<Int>(listJson, "$[0].id")

        mockMvc.perform(get("/api/policies/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("청년 월세 지원"))
            .andExpect(jsonPath("$.mediumCategory").value("전월세 및 주거급여 지원"))
    }

    @Test
    fun `bookmark toggles and appears in saved list`() {
        val token = issueGuestToken()
        val id = JsonPath.read<Int>(
            mockMvc.perform(get("/api/policies").header("Authorization", "Bearer $token"))
                .andReturn().response.contentAsString,
            "$[0].id",
        )

        mockMvc.perform(post("/api/policies/$id/bookmark").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/policies").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$[0].bookmarked").value(true))

        mockMvc.perform(get("/api/bookmarks").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].contentType").value("POLICY"))
            .andExpect(jsonPath("$[0].title").value("청년 월세 지원"))

        mockMvc.perform(delete("/api/policies/$id/bookmark").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/bookmarks").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `tips are empty until data is added`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/tips").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `benefit endpoints require authentication`() {
        mockMvc.perform(get("/api/policies")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/bookmarks")).andExpect(status().isUnauthorized)
    }

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
    }
}
