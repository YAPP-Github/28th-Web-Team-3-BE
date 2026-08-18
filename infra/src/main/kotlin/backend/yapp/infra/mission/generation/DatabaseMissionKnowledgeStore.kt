package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionKnowledge
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalResult
import backend.yapp.core.mission.generation.port.MissionKnowledgeTrace
import backend.yapp.core.mission.generation.port.MissionKnowledgeTracePort
import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate

class DatabaseMissionKnowledgeRetriever(
    private val jdbcTemplate: JdbcTemplate,
) : MissionKnowledgeRetrievalPort {
    override fun retrieve(request: MissionKnowledgeRetrievalRequest): MissionKnowledgeRetrievalResult {
        val candidates = jdbcTemplate.query(
            """
                SELECT id, content, subject_key, official_source_url, valid_from, valid_until
                FROM mission_knowledge
                WHERE category = ?
                  AND item_code = ?
                  AND active = TRUE
                  AND verification_status <> 'REJECTED'
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY id
            """.trimIndent(),
            ::mapKnowledge,
            request.item.category.name,
            request.item.name,
            request.today,
            request.today,
        )
        return MissionKnowledgeRetrievalResult(candidates, candidates.size)
    }

    private fun mapKnowledge(resultSet: ResultSet, rowNumber: Int): MissionKnowledge = MissionKnowledge(
        id = resultSet.getLong("id"),
        content = resultSet.getString("content"),
        subjectKey = resultSet.getString("subject_key"),
        officialSourceUrl = resultSet.getString("official_source_url"),
        validFrom = resultSet.getDate("valid_from")?.toLocalDate(),
        validUntil = resultSet.getDate("valid_until")?.toLocalDate(),
    )
}

class DatabaseMissionKnowledgeTraceRecorder(
    private val jdbcTemplate: JdbcTemplate,
) : MissionKnowledgeTracePort {
    override fun record(trace: MissionKnowledgeTrace) {
        val selectedIds = trace.selectedKnowledgeIds.joinToString(",")
        val updated = jdbcTemplate.update(
            """
                UPDATE mission_knowledge_retrieval_trace
                SET candidate_count = ?, verified_count = ?, selected_knowledge_ids = ?,
                    selection_policy = ?, created_at = CURRENT_TIMESTAMP
                WHERE job_id = ?
            """.trimIndent(),
            trace.candidateCount,
            trace.verifiedCount,
            selectedIds,
            trace.selectionPolicy.name,
            trace.jobId,
        )
        if (updated > 0) return
        jdbcTemplate.update(
            """
                INSERT INTO mission_knowledge_retrieval_trace (
                    job_id, item_code, candidate_count, verified_count,
                    selected_knowledge_ids, selection_policy
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            trace.jobId,
            trace.item.name,
            trace.candidateCount,
            trace.verifiedCount,
            selectedIds,
            trace.selectionPolicy.name,
        )
    }
}

class EmptyMissionKnowledgeRetriever : MissionKnowledgeRetrievalPort {
    override fun retrieve(request: MissionKnowledgeRetrievalRequest): MissionKnowledgeRetrievalResult =
        MissionKnowledgeRetrievalResult(emptyList(), 0)
}

class NoopMissionKnowledgeTraceRecorder : MissionKnowledgeTracePort {
    override fun record(trace: MissionKnowledgeTrace) = Unit
}
