package backend.yapp.api.goal

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `goal status is derived from onboarding and this month saving is overwritten`() {
        val token = completeOnboarding()

        // 이번달 목표 = 매달 모을 금액(100×1.15=115), 목표액 = 순자산 1800 + 115×24 = 4560
        mockMvc.perform(get("/api/goal").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetAmountManwon").value(4560))
            .andExpect(jsonPath("$.periodMonths").value(24))
            .andExpect(jsonPath("$.totalSavedManwon").value(0))
            .andExpect(jsonPath("$.thisMonth.targetManwon").value(115))
            .andExpect(jsonPath("$.thisMonth.savedManwon").value(0))
            .andExpect(jsonPath("$.thisMonth.dDay").isNumber)
            .andExpect(jsonPath("$.thisMonth.dday").doesNotExist())

        // 저축액 입력은 이번 달 값을 덮어쓴다(누적 아님).
        setSaving(token, 30)
        mockMvc.perform(put("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"savedAmountManwon":20}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSavedManwon").value(20))
            .andExpect(jsonPath("$.thisMonth.savedManwon").value(20))

        // 목표 금액/기간 수정
        mockMvc.perform(patch("/api/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"targetAmountManwon":5000,"periodMonths":36}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetAmountManwon").value(5000))
            .andExpect(jsonPath("$.periodMonths").value(36))
            .andExpect(jsonPath("$.totalSavedManwon").value(20))
    }

    @Test
    fun `v2 goal detail includes monthly savings series`() {
        val token = completeOnboarding()
        val thisMonth = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString()

        // 시작월=이번 달 → 월별 현황 1건(이번 달, 저축 0), 기존 현황 필드도 함께 반환
        mockMvc.perform(get("/api/v2/goal").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetAmountManwon").value(4560))
            .andExpect(jsonPath("$.thisMonth.targetManwon").value(115))
            .andExpect(jsonPath("$.monthlySavings.length()").value(1))
            .andExpect(jsonPath("$.monthlySavings[0].yearMonth").value(thisMonth))
            .andExpect(jsonPath("$.monthlySavings[0].savedManwon").value(0))
            .andExpect(jsonPath("$.monthlySavings[0].current").value(true))

        // 이번 달 저축 입력이 월별 현황에도 반영됨
        setSaving(token, 55)
        mockMvc.perform(get("/api/v2/goal").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.totalSavedManwon").value(55))
            .andExpect(jsonPath("$.monthlySavings[0].savedManwon").value(55))
            .andExpect(jsonPath("$.monthlySavings[0].current").value(true))
    }

    @Test
    fun `v2 goal without completed onboarding is rejected`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/v2/goal").header("Authorization", "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("GOAL_ONBOARDING_REQUIRED"))
    }

    @Test
    fun `goal without completed onboarding is rejected`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/goal").header("Authorization", "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("GOAL_ONBOARDING_REQUIRED"))
    }

    @Test
    fun `goal endpoints require authentication`() {
        mockMvc.perform(get("/api/goal"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `negative saving amount is rejected`() {
        val token = completeOnboarding()
        mockMvc.perform(put("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"savedAmountManwon":-1}"""))
            .andExpect(status().isBadRequest)
    }

    private fun setSaving(token: String, amount: Int) {
        mockMvc.perform(put("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"savedAmountManwon":$amount}"""))
            .andExpect(status().isOk)
    }

    private fun completeOnboarding(): String {
        val token = issueGuestToken()
        patchProfile(token, """{"monthlySalaryManwon":350,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1800}""")
        patchProfile(token, """{"goalPeriodMonths":24,"address":"SEOUL"}""")
        mockMvc.perform(post("/api/onboarding/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PLAN_1"}"""))
            .andExpect(status().isCreated)
        return token
    }

    private fun patchProfile(token: String, body: String) {
        mockMvc.perform(patch("/api/onboarding/profile").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
    }

    private fun issueGuestToken(): String {
        val response = mockMvc.perform(post("/api/auth/guest")
            .contentType(MediaType.APPLICATION_JSON).content("""{"uuid":"${UUID.randomUUID()}"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.accessToken")
    }
}
