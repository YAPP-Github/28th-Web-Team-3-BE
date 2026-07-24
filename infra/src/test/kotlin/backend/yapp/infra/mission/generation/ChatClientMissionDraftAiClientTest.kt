package backend.yapp.infra.mission.generation

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.converter.BeanOutputConverter

class ChatClientMissionDraftAiClientTest {
    @Test
    fun `maps structured wrapper response through Spring AI ChatClient`() {
        val model = StubChatModel(
            """{"items":[{"templateId":1,"title":"제목","description":"설명"}]}""",
        )
        val client = ChatClientMissionDraftAiClient(ChatClient.builder(model).build())

        val response = client.generate(MissionDraftAiRequest("SYSTEM", "USER"))

        assertEquals(1L, response.items.single().templateId)
        assertEquals("제목", response.items.single().title)
        assertTrue(model.callCount.get() in 1..3)
        assertTrue(checkNotNull(model.lastPrompt.getSystemMessage().text).contains("SYSTEM"))
        assertTrue(checkNotNull(model.lastPrompt.getUserMessage().text).contains("USER"))
    }

    @Test
    fun `structured wrapper schema rejects unknown output fields`() {
        val schema = BeanOutputConverter(MissionDraftAiResponse::class.java).jsonSchema

        assertContains(schema, "\"additionalProperties\" : false")
        assertContains(schema, "\"items\"")
        assertContains(schema, "\"templateId\"")
        assertContains(schema, "\"title\"")
        assertContains(schema, "\"description\"")
    }

    @Test
    fun `schema validation retries an invalid response within the configured bound`() {
        val model = StubChatModel(
            """{"items":[{"templateId":1,"title":"제목","description":"설명","unknown":"값"}]}""",
            """{"items":[{"templateId":1,"title":"제목","description":"설명"}]}""",
        )
        val client = ChatClientMissionDraftAiClient(ChatClient.builder(model).build())

        val response = client.generate(MissionDraftAiRequest("SYSTEM", "USER"))

        assertEquals(1L, response.items.single().templateId)
        assertEquals(2, model.callCount.get())
    }

    @Test
    fun `schema validation stops after three correction retries`() {
        val invalidResponse =
            """{"items":[{"templateId":1,"title":"제목","description":"설명","unknown":"값"}]}"""
        val model = StubChatModel(invalidResponse)
        val client = ChatClientMissionDraftAiClient(ChatClient.builder(model).build())

        assertFails {
            client.generate(MissionDraftAiRequest("SYSTEM", "USER"))
        }
        assertEquals(4, model.callCount.get())
    }

    @Test
    fun `schema validation corrects missing and null required fields`() {
        val invalidResponses = listOf(
            """{"items":[{"templateId":1,"title":"제목"}]}""",
            """{"items":[{"templateId":1,"title":null,"description":"설명"}]}""",
        )

        invalidResponses.forEach { invalidResponse ->
            val model = StubChatModel(
                invalidResponse,
                """{"items":[{"templateId":1,"title":"제목","description":"설명"}]}""",
            )

            val response = ChatClientMissionDraftAiClient(ChatClient.builder(model).build())
                .generate(MissionDraftAiRequest("SYSTEM", "USER"))

            assertEquals("제목", response.items.single().title)
            assertEquals(2, model.callCount.get())
        }
    }

    private class StubChatModel(
        vararg contents: String,
    ) : ChatModel {
        private val contents = contents.toList()
        val callCount = AtomicInteger()
        lateinit var lastPrompt: Prompt

        override fun call(prompt: Prompt): ChatResponse {
            val callIndex = callCount.getAndIncrement()
            lastPrompt = prompt
            return ChatResponse(
                listOf(Generation(AssistantMessage(contents[callIndex.coerceAtMost(contents.lastIndex)]))),
            )
        }
    }
}
