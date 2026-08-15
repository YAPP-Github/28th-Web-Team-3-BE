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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `full onboarding flow saves each step and completes the goal`() {
        val token = issueGuestToken()

        patchProfile(token, """{"birthDate":"1998-03-01"}""")
        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.address").value("SEOUL"))
        patchProfile(token, """{"monthlySalaryManwon":350,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1800}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")

        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.address").value("SEOUL"))
            .andExpect(jsonPath("$.monthlySalaryManwon").value(350))
            .andExpect(jsonPath("$.goalPeriodMonths").value(24))

        mockMvc.perform(get("/api/onboarding/report").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.simulation.upliftPercent").value(15))
            .andExpect(jsonPath("$.peer.assetRatioPercent").value(36))
            .andExpect(jsonPath("$.diagnosis.branchCode").isNumber)
            .andExpect(jsonPath("$.datasetVersion").value("gafinance-2025-u29"))

        mockMvc.perform(get("/api/onboarding/goal-plans").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plans[0].plan").value("PLAN_1"))
            .andExpect(jsonPath("$.plans[0].default").value(true))
            .andExpect(jsonPath("$.plans.length()").value(2))

        mockMvc.perform(
            post("/api/onboarding/goal")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PLAN_1"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.plan").value("PLAN_1"))
            .andExpect(jsonPath("$.targetAmountManwon").isNumber)

        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(
            post("/api/onboarding/goal")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PLAN_2"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_ALREADY_COMPLETED"))
    }

    @Test
    fun `completed onboarding profile cannot be edited`() {
        val token = issueGuestToken()
        patchProfile(token, """{"birthDate":"1998-03-01","address":"SEOUL"}""")
        patchProfile(token, """{"monthlySalaryManwon":350,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1800}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")
        mockMvc.perform(
            post("/api/onboarding/goal")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"plan":"PLAN_1"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"monthlySavingManwon":150}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_ALREADY_COMPLETED"))

        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `my info can be updated after onboarding is completed`() {
        val token = issueGuestToken()
        patchProfile(token, """{"birthDate":"1998-03-01","address":"SEOUL"}""")
        patchProfile(token, """{"monthlySalaryManwon":350,"monthlySavingManwon":100}""")
        patchProfile(token, """{"netWorthManwon":1800}""")
        patchProfile(token, """{"goalPeriodMonths":24}""")
        mockMvc.perform(
            post("/api/onboarding/goal").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"plan":"PLAN_1"}"""),
        ).andExpect(status().isCreated)

        // PATCH(스텝 저장)는 완료 후 막힘, PUT(내 정보 수정)은 허용
        mockMvc.perform(
            put("/api/onboarding/profile").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"birthDate":"2002-10-24","monthlySalaryManwon":500,"monthlySavingManwon":150,"netWorthManwon":5000,"goalPeriodMonths":36}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.monthlySalaryManwon").value(500))
            .andExpect(jsonPath("$.goalPeriodMonths").value(36))

        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.birthDate").value("2002-10-24"))
            .andExpect(jsonPath("$.monthlySavingManwon").value(150))
            .andExpect(jsonPath("$.netWorthManwon").value(5000))
    }

    @Test
    fun `my info update rejects saving greater than salary`() {
        val token = issueGuestToken()
        mockMvc.perform(
            put("/api/onboarding/profile").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"monthlySalaryManwon":100,"monthlySavingManwon":200}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("INVALID_ONBOARDING_INPUT"))
    }

    @Test
    fun `saving greater than salary is rejected`() {
        val token = issueGuestToken()
        mockMvc.perform(
            patch("/api/onboarding/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"monthlySalaryManwon":100,"monthlySavingManwon":200}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name").value("INVALID_ONBOARDING_INPUT"))
    }

    @Test
    fun `report before required input returns conflict`() {
        val token = issueGuestToken()
        patchProfile(token, """{"birthDate":"1998-03-01","address":"SEOUL"}""")

        mockMvc.perform(get("/api/onboarding/report").header("Authorization", "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.name").value("ONBOARDING_INCOMPLETE"))
    }

    @Test
    fun `profile lookup without any data returns an empty in progress profile`() {
        val token = issueGuestToken()
        mockMvc.perform(get("/api/onboarding/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.birthDate").value(null))
            .andExpect(jsonPath("$.address").value("SEOUL"))
            .andExpect(jsonPath("$.monthlySalaryManwon").value(null))
            .andExpect(jsonPath("$.monthlySavingManwon").value(null))
            .andExpect(jsonPath("$.netWorthManwon").value(null))
            .andExpect(jsonPath("$.goalPeriodMonths").value(null))
    }

    @Test
    fun `onboarding endpoints require authentication`() {
        mockMvc.perform(get("/api/onboarding/profile"))
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
