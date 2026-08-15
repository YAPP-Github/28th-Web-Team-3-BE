package backend.yapp.api.policy

import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "admin.import-token=test-token",
        // 다른 인수 테스트와 in-memory DB(guest-auth)를 공유하지 않도록 독립 DB 사용
        "spring.datasource.url=jdbc:h2:mem:policy-import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyImportAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val sampleJson = """
        {"result":{"pagging":{"totCount":3},"youthPolicyList":[
          {"plcyNo":"UP1","plcyNm":"업로드 월세 지원","lclsfNm":"주거","mclsfNm":"전월세 및 주거급여 지원","plcyExplnCn":"업로드로 저장"},
          {"plcyNo":"UP2","plcyNm":"창업 지원","mclsfNm":"창업"},
          {"plcyNo":"UP3","plcyNm":"청년 자산형성 지원","lclsfNm":"금융･복지･문화","mclsfNm":"취약계층 및 금융지원","plcyExplnCn":"금융 카테고리","aplyUrlAddr":"https://fill4young.kinfa.or.kr/yfs/main"}
        ]}}
    """.trimIndent()

    @Test
    fun `import requires admin token`() {
        mockMvc.perform(multipart("/api/admin/policies/import").file(jsonFile()))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(multipart("/api/admin/policies/import").file(jsonFile()).header("X-Admin-Token", "wrong"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `import parses json, filters out-of-scope, upserts, and serves policies`() {
        // 다른 테스트와 DB를 공유하므로 replace로 깨끗한 상태에서 시작. 스코프 밖(창업) 제외 → 주거·금융 2건 저장
        mockMvc.perform(
            multipart("/api/admin/policies/import").file(jsonFile()).header("X-Admin-Token", "test-token").param("replace", "true"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fetched").value(3))
            .andExpect(jsonPath("$.upserted").value(2))
            .andExpect(jsonPath("$.skipped").value(1))

        // 재업로드는 externalId 기준 upsert(멱등) → 중복 저장되지 않음
        mockMvc.perform(multipart("/api/admin/policies/import").file(jsonFile()).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.upserted").value(2))

        val token = issueGuestToken()
        // 중분류가 4분류(category)로 정규화되어 노출됨
        mockMvc.perform(get("/api/policies").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        // category 필터: 주거
        mockMvc.perform(get("/api/policies?category=주거").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("업로드 월세 지원"))
            .andExpect(jsonPath("$[0].category").value("주거"))

        // category 필터: 금융 (소스 대분류는 '금융･복지･문화'였지만 중분류로 금융 정규화)
        val financeJson = mockMvc.perform(get("/api/policies?category=금융").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("청년 자산형성 지원"))
            .andExpect(jsonPath("$[0].category").value("금융"))
            .andReturn().response.contentAsString

        // applyUrl은 데이터의 안내 URL(aplyUrlAddr)을 그대로 사용
        val id = JsonPath.read<Int>(financeJson, "$[0].id")
        mockMvc.perform(get("/api/policies/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applyUrl").value("https://fill4young.kinfa.or.kr/yfs/main"))
    }

    @Test
    fun `import without applyUrl falls back to 온통청년 detail link`() {
        mockMvc.perform(multipart("/api/admin/policies/import").file(jsonFile()).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
        val token = issueGuestToken()
        // UP1(주거)은 aplyUrlAddr 없음 → plcyNo 기반 온통청년 상세 폴백
        val housingJson = mockMvc.perform(get("/api/policies?category=주거").header("Authorization", "Bearer $token"))
            .andReturn().response.contentAsString
        val id = JsonPath.read<Int>(housingJson, "$[0].id")
        mockMvc.perform(get("/api/policies/$id").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.applyUrl").value("https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch/ythPlcyDetail/UP1"))
    }

    @Test
    fun `replace mode clears existing policies and keeps only uploaded ones`() {
        // 최초 업로드(2건 저장)
        mockMvc.perform(multipart("/api/admin/policies/import").file(jsonFile()).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
        // replace=true로 다른 단일 정책 업로드 → 기존 비우고 이것만
        val other = """
            {"result":{"pagging":{"totCount":1},"youthPolicyList":[
              {"plcyNo":"NEW1","plcyNm":"교체된 정책","mclsfNm":"건강","plcyExplnCn":"replace"}
            ]}}
        """.trimIndent()
        val newFile = MockMultipartFile("file", "new.json", MediaType.APPLICATION_JSON_VALUE, other.toByteArray())
        mockMvc.perform(
            multipart("/api/admin/policies/import").file(newFile).header("X-Admin-Token", "test-token").param("replace", "true"),
        ).andExpect(status().isOk).andExpect(jsonPath("$.upserted").value(1))

        val token = issueGuestToken()
        mockMvc.perform(get("/api/policies").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("교체된 정책"))
    }

    private fun jsonFile() =
        MockMultipartFile("file", "policies.json", MediaType.APPLICATION_JSON_VALUE, sampleJson.toByteArray())

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
    }
}
