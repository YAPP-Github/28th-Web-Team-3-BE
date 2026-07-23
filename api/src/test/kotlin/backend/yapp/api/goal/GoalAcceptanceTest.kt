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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `goal status is derived from onboarding and savings accumulate`() {
        val token = completeOnboarding()

        // 최초 조회: 온보딩 확정값으로 목표 지연 생성. base = 순자산(1800), 이번달 목표 = 월저축(100), 목표액 = 100*1.15*24 = 2760
        mockMvc.perform(get("/api/goal").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetAmountManwon").value(2760))
            .andExpect(jsonPath("$.totalSavedManwon").value(1800))
            .andExpect(jsonPath("$.thisMonth.targetManwon").value(100))
            .andExpect(jsonPath("$.thisMonth.savedManwon").value(0))

        // 저축액 입력은 누적된다.
        addSaving(token, 30)
        mockMvc.perform(post("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"amountManwon":20}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSavedManwon").value(1850)) // 1800 + 30 + 20
            .andExpect(jsonPath("$.thisMonth.savedManwon").value(50))

        // 목표 금액/기간 수정
        mockMvc.perform(patch("/api/goal").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"targetAmountManwon":5000,"periodMonths":36}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetAmountManwon").value(5000))
            .andExpect(jsonPath("$.totalSavedManwon").value(1850))
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
    fun `non-positive saving amount is rejected`() {
        val token = completeOnboarding()
        mockMvc.perform(post("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"amountManwon":0}"""))
            .andExpect(status().isBadRequest)
    }

    private fun addSaving(token: String, amount: Int) {
        mockMvc.perform(post("/api/goal/savings").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"amountManwon":$amount}"""))
            .andExpect(status().isOk)
    }

    private fun completeOnboarding(): String {
        val token = issueGuestToken()
        patchProfile(token, """{"monthlySalaryManwon":350,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1800}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")
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
