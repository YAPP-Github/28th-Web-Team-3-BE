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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "admin.import-token=test-token",
        "spring.datasource.url=jdbc:h2:mem:policy-region;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyRegionFilterAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    // 모두 '건강'(복지). zipCd 앞 2자리: 11=서울, 26=부산, 전국=16개 시도 코드 모두.
    private val json = """
        {"result":{"pagging":{"totCount":3},"youthPolicyList":[
          {"plcyNo":"R_SEOUL","plcyNm":"서울 청년 정책","mclsfNm":"건강","zipCd":"11110,11140"},
          {"plcyNo":"R_BUSAN","plcyNm":"부산 청년 정책","mclsfNm":"건강","zipCd":"26110,26140"},
          {"plcyNo":"R_ALL","plcyNm":"전국 청년 정책","mclsfNm":"건강","zipCd":"11110,12110,26110,27110,28110,30110,31110,36110,41110,43110,44110,47110,48110,50110,51110,52110"}
        ]}}
    """.trimIndent()

    @BeforeEach
    fun importPolicies() {
        val file = MockMultipartFile("file", "region.json", MediaType.APPLICATION_JSON_VALUE, json.toByteArray())
        mockMvc.perform(multipart("/api/admin/policies/import").file(file).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `guest without address sees all regional policies`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `seoul guest sees seoul and nationwide policies only`() {
        val token = issueGuestToken()
        setAddress(token, "SEOUL")

        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.title=='서울 청년 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='전국 청년 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='부산 청년 정책')]").doesNotExist())
    }

    @Test
    fun `busan guest sees busan and nationwide policies only`() {
        val token = issueGuestToken()
        setAddress(token, "BUSAN")

        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.title=='부산 청년 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='전국 청년 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='서울 청년 정책')]").doesNotExist())
    }

    private fun setAddress(token: String, address: String) {
        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"address":"$address"}"""),
        ).andExpect(status().isOk)
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
