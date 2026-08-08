package backend.yapp.api.policy

import com.jayway.jsonpath.JsonPath
import java.time.LocalDate
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
        "spring.datasource.url=jdbc:h2:mem:policy-age;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyAgeFilterAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    // 모두 '건강'(복지)이라 category=복지로 격리해 확인한다. 연령 범위만 다르게 둔다.
    private val ageJson = """
        {"result":{"pagging":{"totCount":3},"youthPolicyList":[
          {"plcyNo":"AGE_IN","plcyNm":"연령 해당 정책","mclsfNm":"건강","sprtTrgtMinAge":"19","sprtTrgtMaxAge":"34"},
          {"plcyNo":"AGE_OLD","plcyNm":"연령 초과 정책","mclsfNm":"건강","sprtTrgtMinAge":"30","sprtTrgtMaxAge":"39"},
          {"plcyNo":"AGE_OPEN","plcyNm":"연령 무제한 정책","mclsfNm":"건강"}
        ]}}
    """.trimIndent()

    @BeforeEach
    fun importPolicies() {
        val file = MockMultipartFile("file", "age.json", MediaType.APPLICATION_JSON_VALUE, ageJson.toByteArray())
        mockMvc.perform(multipart("/api/admin/policies/import").file(file).header("X-Admin-Token", "test-token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `guest without birthDate sees all policies regardless of age`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `guest with birthDate only sees policies matching their age`() {
        val token = issueGuestToken()
        // 만 25세가 되도록 생년월일 설정(경계 회피 위해 6개월 여유)
        val birthDate = LocalDate.now().minusYears(25).minusMonths(6)
        setBirthDate(token, birthDate)

        // 25세: 30~39 정책 제외, 19~34 및 연령무제한 포함 → 2건
        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.title=='연령 해당 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='연령 무제한 정책')]").exists())
            .andExpect(jsonPath("$[?(@.title=='연령 초과 정책')]").doesNotExist())
    }

    @Test
    fun `older guest is excluded from youth-only policies`() {
        val token = issueGuestToken()
        // 만 45세: 19~34, 30~39 모두 초과 → 연령무제한 1건만
        setBirthDate(token, LocalDate.now().minusYears(45).minusMonths(6))

        mockMvc.perform(get("/api/policies?category=복지").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("연령 무제한 정책"))
    }

    private fun setBirthDate(token: String, birthDate: LocalDate) {
        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"birthDate":"$birthDate"}"""),
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
