package backend.yapp.api.policy

import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
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
        "spring.datasource.url=jdbc:h2:mem:policy-viewcount;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyViewCountSortAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    // 모두 '건강'(복지). inqCnt(조회수)만 다르게.
    private val json = """
        {"result":{"pagging":{"totCount":3},"youthPolicyList":[
          {"plcyNo":"V_MID","plcyNm":"중간 조회수","mclsfNm":"건강","inqCnt":"50"},
          {"plcyNo":"V_HIGH","plcyNm":"높은 조회수","mclsfNm":"건강","inqCnt":"1000"},
          {"plcyNo":"V_LOW","plcyNm":"낮은 조회수","mclsfNm":"건강","inqCnt":"5"}
        ]}}
    """.trimIndent()

    @BeforeEach
    fun importPolicies() {
        val file = MockMultipartFile("file", "views.json", MediaType.APPLICATION_JSON_VALUE, json.toByteArray())
        mockMvc.perform(multipart("/api/admin/policies/import").file(file).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `policies are ordered by view count descending`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].title").value("높은 조회수"))
            .andExpect(jsonPath("$[0].viewCount").value(1000))
            .andExpect(jsonPath("$[1].title").value("중간 조회수"))
            .andExpect(jsonPath("$[1].viewCount").value(50))
            .andExpect(jsonPath("$[2].title").value("낮은 조회수"))
            .andExpect(jsonPath("$[2].viewCount").value(5))
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
