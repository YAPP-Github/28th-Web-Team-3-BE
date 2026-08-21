package backend.yapp.api.tip

import com.jayway.jsonpath.JsonPath
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
class TipAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `tip list returns seeded data with category and subcategory filters`() {
        val token = issueGuestToken()

        // 전체(시드 29건) — 요약에 제목·카테고리·원문URL 포함
        mockMvc.perform(get("/api/tips?size=100").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(29))
            .andExpect(jsonPath("$[0].title").value("집밥 레시피 활용팁"))
            .andExpect(jsonPath("$[0].category").value("식비"))
            .andExpect(jsonPath("$[0].subcategory").value("배달음식"))
            .andExpect(jsonPath("$[0].sourceUrl").value("https://www.youtube.com/watch?v=nZw2A76aZaw"))
            .andExpect(jsonPath("$[0].bookmarked").value(false))

        // 카테고리 필터(식비 = 8건)
        mockMvc.perform(get("/api/tips?category=식비&size=100").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$[*].category", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.`is`("식비"))))

        // 선택항목 필터(편의점 = 4건)
        mockMvc.perform(get("/api/tips?subcategory=편의점&size=100").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))
    }

    @Test
    fun `tip detail exposes source url and subcategory`() {
        val token = issueGuestToken()
        val id = firstTipId(token)

        mockMvc.perform(get("/api/tips/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").isNotEmpty)
            .andExpect(jsonPath("$.sourceUrl").isNotEmpty)
            .andExpect(jsonPath("$.subcategory").isNotEmpty)
            .andExpect(jsonPath("$.bookmarked").value(false))
    }

    @Test
    fun `tip bookmark appears in saved list and toggles bookmarked flag`() {
        val token = issueGuestToken()
        val id = firstTipId(token)

        // 저장
        mockMvc.perform(post("/api/tips/$id/bookmark").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        // 목록에서 bookmarked=true
        mockMvc.perform(get("/api/tips?size=100").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$[0].bookmarked").value(true))

        // 저장됨(북마크) 목록(TIP)에 노출
        mockMvc.perform(get("/api/bookmarks?type=TIP").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].contentType").value("TIP"))
            .andExpect(jsonPath("$[0].id").value(id))

        // 저장 취소 → 목록에서 사라짐
        mockMvc.perform(delete("/api/tips/$id/bookmark").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/bookmarks?type=TIP").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `tip endpoints require authentication`() {
        mockMvc.perform(get("/api/tips"))
            .andExpect(status().isUnauthorized)
    }

    private fun firstTipId(token: String): Int {
        val body = mockMvc.perform(get("/api/tips?size=1").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.read<List<Int>>(body, "$[*].id").first()
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
