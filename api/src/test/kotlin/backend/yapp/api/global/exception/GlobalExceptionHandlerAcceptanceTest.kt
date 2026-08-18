package backend.yapp.api.global.exception

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `unknown api path returns not found error`() {
        val token = guestToken()

        mockMvc.perform(
            get("/api/unknown-resource")
                .header(AUTHORIZATION, "Bearer $token"),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.name").value("NO_HANDLER_FOUND"))
    }

    private fun guestToken(): String {
        val body = mockMvc.perform(
            post("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"uuid":"${UUID.randomUUID()}"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
    }
}
