package backend.yapp.api.onboarding

import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingGoalV2AcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `v2 goal preview recalculates with net worth and caps slider max by salary`() {
        val token = issueGuestToken()
        // 월급 130, 월저축 100 → 슬라이더 최댓값 = MIN(100×1.5=150, 130) = 130
        patchProfile(token, """{"monthlySalaryManwon":130,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":2500}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")

        // 슬라이더 130만원(= 월급 상한) → 예상 = 순자산 2500 + 130×24(3120) = 5620
        mockMvc.perform(get("/api/v2/onboarding/goal-preview?monthlySavingManwon=130").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.monthlySavingManwon").value(130))
            .andExpect(jsonPath("$.baseAmountManwon").value(2500))
            .andExpect(jsonPath("$.additionalSavingManwon").value(3120))
            .andExpect(jsonPath("$.expectedAmountManwon").value(5620))
            .andExpect(jsonPath("$.minMonthlySavingManwon").value(100))
            .andExpect(jsonPath("$.maxMonthlySavingManwon").value(130)) // 150이 아닌 월급 130으로 제한
            .andExpect(jsonPath("$.recommendedMonthlySavingManwon").value(115))
            .andExpect(jsonPath("$.extraMonthlyManwon").value(30))
            .andExpect(jsonPath("$.extraPercent").value(30))

        // 파라미터 없으면 권장값(현재+15%=115)로 계산
        mockMvc.perform(get("/api/v2/onboarding/goal-preview").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.monthlySavingManwon").value(115))

        // 현재 저축액 미만/슬라이더 최댓값(=월급 130) 초과는 400
        mockMvc.perform(get("/api/v2/onboarding/goal-preview?monthlySavingManwon=90").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v2/onboarding/goal-preview?monthlySavingManwon=140").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `v2 goal preview clamps recommended to salary cap`() {
        val token = issueGuestToken()
        // 월급 110, 월저축 100 → 최댓값 = MIN(150, 110) = 110, 권장(115)도 110으로 clamp
        patchProfile(token, """{"monthlySalaryManwon":110,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1000}""")
        patchProfile(token, """{"goalPeriodMonths":12}""")

        mockMvc.perform(get("/api/v2/onboarding/goal-preview").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.maxMonthlySavingManwon").value(110))
            .andExpect(jsonPath("$.recommendedMonthlySavingManwon").value(110))
            .andExpect(jsonPath("$.monthlySavingManwon").value(110))
    }

    @Test
    fun `v2 confirm without plan includes net worth and completes onboarding`() {
        val token = issueGuestToken()
        patchProfile(token, """{"monthlySalaryManwon":500,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":2500}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")

        // 슬라이더 130만원 확정 → 목표액 = 순자산 2500 + 130×24 = 5620
        mockMvc.perform(post("/api/v2/onboarding/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"monthlySavingManwon":130}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.targetAmountManwon").value(5620))
            .andExpect(jsonPath("$.plan").doesNotExist())

        mockMvc.perform(get("/api/goal").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.targetAmountManwon").value(5620))
            .andExpect(jsonPath("$.thisMonth.targetManwon").value(130))

        // 이미 완료됐으면 409
        mockMvc.perform(post("/api/v2/onboarding/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"monthlySavingManwon":120}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_ALREADY_COMPLETED"))
    }

    @Test
    fun `v2 confirm requires monthly saving`() {
        val token = issueGuestToken()
        patchProfile(token, """{"monthlySalaryManwon":500,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":2500}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")

        mockMvc.perform(post("/api/v2/onboarding/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `v2 goal endpoints require authentication`() {
        mockMvc.perform(get("/api/v2/onboarding/goal-preview"))
            .andExpect(status().isUnauthorized)
    }

    private fun patchProfile(token: String, body: String) {
        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
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
