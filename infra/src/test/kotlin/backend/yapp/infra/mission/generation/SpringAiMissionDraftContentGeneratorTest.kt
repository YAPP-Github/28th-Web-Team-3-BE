package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import tools.jackson.databind.ObjectMapper

class SpringAiMissionDraftContentGeneratorTest {
    private val objectMapper = ObjectMapper()
    private val prompt = MissionPromptProperties(
        version = "mission-copy-test-v1",
        systemInstruction = "SYSTEM CONSTRAINT",
        userInstruction = "USER CONSTRAINT",
    )

    @Test
    fun `sends versioned bounded candidate data and returns validated copy`() {
        val captured = AtomicReference<MissionDraftAiRequest>()
        val generator = generator { request ->
            captured.set(request)
            MissionDraftAiResponse(
                listOf(MissionDraftAiCopy(1, "생성 제목", "생성 설명")),
            )
        }

        val result = generator.generate(request())

        assertEquals(MissionDraftGenerationSource.AI, result.source)
        assertEquals("생성 제목", result.copies.single().title)
        assertEquals("SYSTEM CONSTRAINT", captured.get().systemInstruction)
        assertContains(captured.get().userInstruction, "USER CONSTRAINT")
        assertContains(captured.get().userInstruction, "<prompt-version>mission-copy-test-v1</prompt-version>")
        assertContains(captured.get().userInstruction, "<candidate-data>")
        assertContains(captured.get().userInstruction, "\"targetCount\":1")
        assertContains(captured.get().userInstruction, "</candidate-data>")
    }

    @Test
    fun `falls back without calling AI when candidate ids are duplicated`() {
        var callCount = 0
        val generator = generator {
            callCount++
            error("must not be called")
        }

        val result = generator.generate(
            request(
                candidates = listOf(candidate(1), candidate(1)),
            ),
        )

        assertEquals(0, callCount)
        assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
        assertEquals(listOf("기본 제목 1", "기본 제목 1"), result.copies.map { it.title })
    }

    @Test
    fun `falls back when AI changes omits or duplicates candidate identity`() {
        val invalidResponses = listOf(
            MissionDraftAiResponse(listOf(MissionDraftAiCopy(999, "제목", "설명"))),
            MissionDraftAiResponse(emptyList()),
            MissionDraftAiResponse(
                listOf(
                    MissionDraftAiCopy(1, "제목", "설명"),
                    MissionDraftAiCopy(1, "중복", "중복"),
                ),
            ),
        )
        invalidResponses.forEach { response ->
            val candidates = if (response.items.size == 2) {
                listOf(candidate(1), candidate(2))
            } else {
                listOf(candidate(1))
            }

            val result = generator { response }.generate(request(candidates))

            assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
            assertEquals(candidates.map { it.templateTitle }, result.copies.map { it.title })
        }
    }

    @Test
    fun `falls back when AI copy is blank or exceeds length contract`() {
        val invalidCopies = listOf(
            MissionDraftAiCopy(1, " ", "설명"),
            MissionDraftAiCopy(1, "제목", ""),
            MissionDraftAiCopy(1, "가".repeat(121), "설명"),
            MissionDraftAiCopy(1, "제목", "가".repeat(501)),
        )
        invalidCopies.forEach { copy ->
            val result = generator {
                MissionDraftAiResponse(listOf(copy))
            }.generate(request())

            assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
            assertEquals("기본 제목 1", result.copies.single().title)
        }
    }

    @Test
    fun `falls back when Spring AI client fails`() {
        val result = generator {
            error("provider unavailable")
        }.generate(request())

        assertEquals(MissionDraftGenerationSource.TEMPLATE_FALLBACK, result.source)
        assertEquals("기본 제목 1", result.copies.single().title)
    }

    private fun generator(client: MissionDraftAiClient): SpringAiMissionDraftContentGenerator =
        SpringAiMissionDraftContentGenerator(client, objectMapper, prompt)

    private fun request(
        candidates: List<MissionDraftCandidate> = listOf(candidate(1)),
    ): MissionDraftContentRequest =
        MissionDraftContentRequest(
            jobId = UUID.randomUUID(),
            guestUserId = 7,
            candidates = candidates,
        )

    private fun candidate(id: Long): MissionDraftCandidate =
        MissionDraftCandidate(
            templateId = id,
            category = MissionCategory.MEAL,
            templateTitle = "기본 제목 $id",
            templateDescription = "기본 설명 $id",
            actionCode = "ACTION_$id",
            metricType = MissionMetricType.COUNT,
            targetCount = 1,
            targetUnit = "TIMES_PER_WEEK",
            estimatedSavingsWon = 5_000,
        )
}
