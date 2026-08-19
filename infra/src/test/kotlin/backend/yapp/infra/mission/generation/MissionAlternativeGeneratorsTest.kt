package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionKnowledge
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

class MissionAlternativeGeneratorsTest {
    @Test
    fun `rejects a structurally valid provider response with a non-frequency count unit`() {
        val model = StubChatModel(
            """
                {"items":[
                  {"titleTemplate":"집에서 즐기는 {count}가지 레시피","description":"설명"},
                  {"titleTemplate":"포장 주문으로 {count}번의 배달비 줄이기","description":"설명"},
                  {"titleTemplate":"주문 전 예산을 {count}회 확인하기","description":"설명"}
                ]}
            """.trimIndent(),
        )
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        assertFailsWith<IllegalArgumentException> {
            generator.generate(MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()))
        }
        assertEquals(1, model.callCount.get())
    }

    @Test
    fun `accepts a valid action frequency and instructs the model about weekly count semantics`() {
        val model = StubChatModel(
            validAlternativesResponse(),
        )
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        val result = generator.generate(
            MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()),
        )

        assertEquals(3, result.alternatives.size)
        assertEquals("포장 주문으로 {count}번의 배달비 줄이기", result.alternatives.first().titleTemplate)
        assertContains(checkNotNull(model.lastPrompt.getSystemMessage().text), "주간 실행 횟수")
        assertContains(checkNotNull(model.lastPrompt.getSystemMessage().text), "{count}회 또는 {count}번")
        assertContains(checkNotNull(model.lastPrompt.getSystemMessage().text), "서로 다른 대안 1~3개")
    }

    @Test
    fun `instructs the model to generate alternatives within provided knowledge`() {
        val model = StubChatModel(validAlternativesResponse())
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        generator.generate(
            MissionAlternativeGenerationRequest(
                item = MissionItem.CONVENIENCE_STORE,
                knowledgeContexts = listOf(
                    MissionKnowledge(7, "편의점 페이백 이벤트 혜택 활용하기", null, null, null, null),
                ),
                personalizationContext = "항목=편의점 | 사용자=20대 서울 | 소비=3회 30000원",
            ),
        )

        val systemMessage = checkNotNull(model.lastPrompt.getSystemMessage().text)
        val userMessage = checkNotNull(model.lastPrompt.getUserMessage().text)
        assertContains(systemMessage, "제공된 지식이 있으면 그 범위 안에서 구체적인 미션을 생성")
        assertContains(userMessage, "knowledgeId=7")
        assertContains(userMessage, "항목=편의점 | 사용자=20대 서울 | 소비=3회 30000원")
    }

    @Test
    fun `rejects a provider response that does not contain exactly three alternatives`() {
        val model = StubChatModel(
            """
                {"items":[
                  {"titleTemplate":"포장 주문으로 {count}번의 배달비 줄이기","description":"설명"},
                  {"titleTemplate":"집밥으로 {count}회 지출 줄이기","description":"설명"}
                ]}
            """.trimIndent(),
        )
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        assertFailsWith<IllegalStateException> {
            generator.generate(MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()))
        }
    }

    @Test
    fun `rejects more than one knowledge context`() {
        val model = StubChatModel(validAlternativesResponse())
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())
        val contexts = listOf(
            MissionKnowledge(1, "지식 1", null, null, null, null),
            MissionKnowledge(2, "지식 2", null, null, null, null),
        )

        assertFailsWith<IllegalArgumentException> {
            generator.generate(MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, contexts))
        }
        assertEquals(0, model.callCount.get())
    }

    private fun validAlternativesResponse(): String =
        """
            {"items":[
              {"titleTemplate":"포장 주문으로 {count}번의 배달비 줄이기","description":"설명"},
              {"titleTemplate":"집밥으로 {count}회 지출 줄이기","description":"설명"},
              {"titleTemplate":"주문 전 예산을 {count}회 확인하기","description":"설명"}
            ]}
        """.trimIndent()

    private class StubChatModel(
        private val content: String,
    ) : ChatModel {
        val callCount = AtomicInteger()
        lateinit var lastPrompt: Prompt

        override fun call(prompt: Prompt): ChatResponse {
            callCount.incrementAndGet()
            lastPrompt = prompt
            return ChatResponse(listOf(Generation(AssistantMessage(content))))
        }
    }
}
