package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.port.MissionKnowledge
import backend.yapp.core.mission.generation.port.MissionKnowledgeSelectionPolicy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionKnowledgeSelectorTest {
    @Test
    fun `empty candidates select no knowledge`() {
        val selection = MissionKnowledgeSelector.select(JOB_ID, emptyList())

        assertEquals(emptyList(), selection.knowledge)
        assertEquals(MissionKnowledgeSelectionPolicy.EMPTY, selection.policy)
    }

    @Test
    fun `one candidate is selected as is`() {
        val candidates = listOf(knowledge(1))

        val selection = MissionKnowledgeSelector.select(JOB_ID, candidates)

        assertEquals(candidates, selection.knowledge)
        assertEquals(MissionKnowledgeSelectionPolicy.ALL, selection.policy)
    }

    @Test
    fun `more than one candidate selects the same one for the same job`() {
        val candidates = (1L..9L).map(::knowledge)

        val first = MissionKnowledgeSelector.select(JOB_ID, candidates)
        val retry = MissionKnowledgeSelector.select(JOB_ID, candidates.reversed())

        assertEquals(1, first.knowledge.size)
        assertEquals(1, first.knowledge.distinctBy { it.id }.size)
        assertEquals(first.knowledge, retry.knowledge)
        assertEquals(MissionKnowledgeSelectionPolicy.DETERMINISTIC_RANDOM_1, first.policy)
    }

    private fun knowledge(id: Long) = MissionKnowledge(id, "지식 $id", null, null, null, null)

    companion object {
        private val JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    }
}
