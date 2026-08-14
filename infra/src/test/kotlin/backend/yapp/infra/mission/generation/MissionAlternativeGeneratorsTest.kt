package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

class MissionAlternativeGeneratorsTest {
    @Test
    fun `falls back when a provider response uses a non-frequency count unit`() {
        val model = StubChatModel(
            """{"items":[{"titleTemplate":"집에서 즐기는 {count}가지 레시피","description":"설명"}]}""",
        )
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        val result = generator.generate(
            MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()),
        )

        assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
        assertEquals(3, result.alternatives.size)
        assertEquals(1, model.callCount.get())
    }

    @Test
    fun `falls back when the provider call fails`() {
        val model = StubChatModel(error = IllegalStateException("provider failed"))
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        val result = generator.generate(
            MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()),
        )

        assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
        assertEquals(3, result.alternatives.size)
        assertEquals(1, model.callCount.get())
    }

    @Test
    fun `accepts a valid action frequency and instructs the model about weekly count semantics`() {
        val model = StubChatModel(
            """{"items":[{"titleTemplate":"포장 주문으로 {count}번의 배달비 줄이기","description":"설명"}]}""",
        )
        val generator = SpringAiMissionAlternativeGenerator(ChatClient.builder(model).build())

        val result = generator.generate(
            MissionAlternativeGenerationRequest(MissionItem.DELIVERY_FOOD, emptyList()),
        )

        assertEquals("포장 주문으로 {count}번의 배달비 줄이기", result.alternatives.single().titleTemplate)
        assertContains(checkNotNull(model.lastPrompt.getSystemMessage().text), "주간 실행 횟수")
        assertContains(checkNotNull(model.lastPrompt.getSystemMessage().text), "{count}회 또는 {count}번")
    }

    private class StubChatModel(
        private val content: String? = null,
        private val error: RuntimeException? = null,
    ) : ChatModel {
        val callCount = AtomicInteger()
        lateinit var lastPrompt: Prompt

        override fun call(prompt: Prompt): ChatResponse {
            callCount.incrementAndGet()
            lastPrompt = prompt
            error?.let { throw it }
            return ChatResponse(listOf(Generation(AssistantMessage(checkNotNull(content)))))
        }
    }
}
