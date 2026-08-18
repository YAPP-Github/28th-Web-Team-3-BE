package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionKnowledge
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalPort
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalRequest
import backend.yapp.core.mission.generation.port.MissionKnowledgeRetrievalResult
import backend.yapp.core.mission.generation.port.MissionKnowledgeSelectionPolicy
import java.sql.ResultSet
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.jdbc.core.JdbcTemplate

class PgVectorMissionKnowledgeRetriever(
    private val jdbcTemplate: JdbcTemplate,
    private val embeddingModel: EmbeddingModel?,
    private val embeddingModelVersion: String,
) : MissionKnowledgeRetrievalPort {
    override fun retrieve(request: MissionKnowledgeRetrievalRequest): MissionKnowledgeRetrievalResult {
        val candidates = findCandidates(request)
        val selection = when {
            candidates.isEmpty() -> Selection(emptyList(), MissionKnowledgeSelectionPolicy.EMPTY)
            candidates.size <= ALL_CANDIDATE_LIMIT -> Selection(candidates, MissionKnowledgeSelectionPolicy.ALL)
            embeddingModel == null -> Selection(candidates.take(TOP_K), MissionKnowledgeSelectionPolicy.FALLBACK_TOP_5)
            else -> semanticTopK(request, candidates)
        }
        saveTrace(request, candidates.size, selection)
        return MissionKnowledgeRetrievalResult(
            knowledge = expandSelectedConflictGroups(selection.knowledge, candidates),
            candidateCount = candidates.size,
            policy = selection.policy,
        )
    }

    private fun findCandidates(request: MissionKnowledgeRetrievalRequest): List<MissionKnowledge> =
        jdbcTemplate.query(
            """
                SELECT id, content, subject_key, official_source_url, valid_from, valid_until,
                       1 - (embedding <=> CAST(? AS extensions.vector)) AS similarity
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

    private fun semanticTopK(
        request: MissionKnowledgeRetrievalRequest,
        candidates: List<MissionKnowledge>,
    ): Selection = runCatching {
        embedMissing(request)
        val queryVector = vectorLiteral(checkNotNull(embeddingModel).embed(request.queryText))
        val selected = jdbcTemplate.query(
            """
                SELECT id, content, subject_key, official_source_url, valid_from, valid_until
                FROM mission_knowledge
                WHERE category = ?
                  AND item_code = ?
                  AND active = TRUE
                  AND verification_status <> 'REJECTED'
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                  AND embedding IS NOT NULL
                ORDER BY similarity DESC, id
                LIMIT $TOP_K
            """.trimIndent(),
            ::mapScoredKnowledge,
            queryVector,
            request.item.category.name,
            request.item.name,
            request.today,
            request.today,
        )
        check(selected.isNotEmpty()) { "No embedded knowledge was selectable" }
        Selection(selected, MissionKnowledgeSelectionPolicy.SEMANTIC_TOP_5)
    }.onFailure { exception ->
        log.warn("mission_knowledge.semantic_retrieval.fallback item={}", request.item, exception)
    }.getOrElse {
        Selection(candidates.take(TOP_K), MissionKnowledgeSelectionPolicy.FALLBACK_TOP_5)
    }

    private fun embedMissing(request: MissionKnowledgeRetrievalRequest) {
        val missing = jdbcTemplate.query(
            """
                SELECT id, content
                FROM mission_knowledge
                WHERE category = ?
                  AND item_code = ?
                  AND active = TRUE
                  AND verification_status <> 'REJECTED'
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                  AND (embedding IS NULL OR embedding_model IS DISTINCT FROM ? OR embedded_content IS DISTINCT FROM content)
                ORDER BY id
            """.trimIndent(),
            { resultSet, _ -> resultSet.getLong("id") to resultSet.getString("content") },
            request.item.category.name,
            request.item.name,
            request.today,
            request.today,
            embeddingModelVersion,
        )
        if (missing.isEmpty()) return
        val embeddings = checkNotNull(embeddingModel).embed(missing.map { it.second })
        require(embeddings.size == missing.size) { "Embedding count did not match mission knowledge count" }
        missing.zip(embeddings).forEach { (knowledge, vector) ->
            jdbcTemplate.update(
                """
                    UPDATE mission_knowledge
                    SET embedding = CAST(? AS extensions.vector),
                        embedding_model = ?,
                        embedded_content = content,
                        embedded_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """.trimIndent(),
                vectorLiteral(vector),
                embeddingModelVersion,
                knowledge.first,
            )
        }
    }

    private fun saveTrace(
        request: MissionKnowledgeRetrievalRequest,
        candidateCount: Int,
        selection: Selection,
    ) {
        val selectedIds = selection.knowledge.joinToString(",") { it.id.toString() }
        val similarityScores = selection.knowledge.mapNotNull { it.similarityScore }.joinToString(",")
        val traceEmbeddingModel = embeddingModelVersion.takeIf {
            selection.policy == MissionKnowledgeSelectionPolicy.SEMANTIC_TOP_5
        }
        val updated = jdbcTemplate.update(
            """
                UPDATE mission_knowledge_retrieval_trace
                SET query_text = ?, candidate_count = ?, selected_knowledge_ids = ?,
                    selected_similarity_scores = ?, selection_policy = ?, embedding_model = ?,
                    created_at = CURRENT_TIMESTAMP
                WHERE job_id = ?
            """.trimIndent(),
            request.queryText,
            candidateCount,
            selectedIds,
            similarityScores,
            selection.policy.name,
            traceEmbeddingModel,
            request.jobId,
        )
        if (updated > 0) return
        jdbcTemplate.update(
            """
                INSERT INTO mission_knowledge_retrieval_trace (
                    job_id, item_code, query_text, candidate_count, selected_knowledge_ids,
                    selected_similarity_scores, selection_policy, embedding_model
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            request.jobId,
            request.item.name,
            request.queryText,
            candidateCount,
            selectedIds,
            similarityScores,
            selection.policy.name,
            traceEmbeddingModel,
        )
    }

    private fun expandSelectedConflictGroups(
        selected: List<MissionKnowledge>,
        candidates: List<MissionKnowledge>,
    ): List<MissionKnowledge> {
        val conflictingSubjectKeys = candidates
            .filter { it.subjectKey != null }
            .groupBy { it.subjectKey }
            .filterValues { group -> group.map { it.content.trim() }.distinct().size > 1 }
            .keys
        val selectedConflictKeys = selected.mapNotNull { it.subjectKey }.filterTo(mutableSetOf()) {
            it in conflictingSubjectKeys
        }
        if (selectedConflictKeys.isEmpty()) return selected
        return (selected + candidates.filter { it.subjectKey in selectedConflictKeys })
            .distinctBy(MissionKnowledge::id)
    }

    private fun mapKnowledge(resultSet: ResultSet, rowNumber: Int): MissionKnowledge = MissionKnowledge(
        id = resultSet.getLong("id"),
        content = resultSet.getString("content"),
        subjectKey = resultSet.getString("subject_key"),
        officialSourceUrl = resultSet.getString("official_source_url"),
        validFrom = resultSet.getDate("valid_from")?.toLocalDate(),
        validUntil = resultSet.getDate("valid_until")?.toLocalDate(),
    )

    private fun mapScoredKnowledge(resultSet: ResultSet, rowNumber: Int): MissionKnowledge = MissionKnowledge(
        id = resultSet.getLong("id"),
        content = resultSet.getString("content"),
        subjectKey = resultSet.getString("subject_key"),
        officialSourceUrl = resultSet.getString("official_source_url"),
        validFrom = resultSet.getDate("valid_from")?.toLocalDate(),
        validUntil = resultSet.getDate("valid_until")?.toLocalDate(),
        similarityScore = resultSet.getDouble("similarity"),
    )

    private fun vectorLiteral(vector: FloatArray): String = vector.joinToString(prefix = "[", postfix = "]")

    private data class Selection(
        val knowledge: List<MissionKnowledge>,
        val policy: MissionKnowledgeSelectionPolicy,
    )

    companion object {
        private val log = LoggerFactory.getLogger(PgVectorMissionKnowledgeRetriever::class.java)
        private const val ALL_CANDIDATE_LIMIT = 5
        private const val TOP_K = 5
    }
}

class EmptyMissionKnowledgeRetriever : MissionKnowledgeRetrievalPort {
    override fun retrieve(request: MissionKnowledgeRetrievalRequest): MissionKnowledgeRetrievalResult =
        MissionKnowledgeRetrievalResult(
            knowledge = emptyList(),
            candidateCount = 0,
            policy = MissionKnowledgeSelectionPolicy.EMPTY,
        )
}
