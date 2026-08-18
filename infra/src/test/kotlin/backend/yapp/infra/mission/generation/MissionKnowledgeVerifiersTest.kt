package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionKnowledge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissionKnowledgeVerifiersTest {
    @Test
    fun `conservative verifier keeps ordinary knowledge and removes contradictory subject group`() {
        val ordinary = knowledge(1, "일반 혜택", "ORDINARY")
        val conflicting = listOf(
            knowledge(2, "할인율 10퍼센트", "SAME_EVENT"),
            knowledge(3, "할인율 20퍼센트", "SAME_EVENT"),
        )

        val verified = ConservativeMissionKnowledgeVerifier().verify(listOf(ordinary) + conflicting)

        assertEquals(listOf(ordinary), verified)
    }

    @Test
    fun `official source reader rejects loopback URLs before requesting them`() {
        assertFailsWith<IllegalArgumentException> {
            SafeOfficialSourceReader().read("https://127.0.0.1/notice")
        }
    }

    private fun knowledge(id: Long, content: String, subjectKey: String) = MissionKnowledge(
        id = id,
        content = content,
        subjectKey = subjectKey,
        officialSourceUrl = null,
        validFrom = null,
        validUntil = null,
    )
}
