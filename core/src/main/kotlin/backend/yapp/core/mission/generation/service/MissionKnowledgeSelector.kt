package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.port.MissionKnowledge
import backend.yapp.core.mission.generation.port.MissionKnowledgeSelectionPolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object MissionKnowledgeSelector {
    fun select(jobId: UUID, candidates: List<MissionKnowledge>): MissionKnowledgeSelection = when {
        candidates.isEmpty() -> MissionKnowledgeSelection(emptyList(), MissionKnowledgeSelectionPolicy.EMPTY)
        candidates.size == MAX_SELECTION_SIZE ->
            MissionKnowledgeSelection(candidates, MissionKnowledgeSelectionPolicy.ALL)
        else -> MissionKnowledgeSelection(
            knowledge = candidates
                .sortedBy { knowledge -> stableRandomKey(jobId, knowledge.id) }
                .take(MAX_SELECTION_SIZE),
            policy = MissionKnowledgeSelectionPolicy.DETERMINISTIC_RANDOM_1,
        )
    }

    private fun stableRandomKey(jobId: UUID, knowledgeId: Long): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$jobId:$knowledgeId".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val MAX_SELECTION_SIZE = 1
}

data class MissionKnowledgeSelection(
    val knowledge: List<MissionKnowledge>,
    val policy: MissionKnowledgeSelectionPolicy,
)
