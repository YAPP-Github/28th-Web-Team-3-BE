package backend.yapp.core.mission.generation.port

data class MissionSemanticRetrievalRequest(
    val query: String,
    val candidates: List<MissionSemanticDocument>,
)

data class MissionSemanticDocument(
    val templateId: Long,
    val text: String,
)

data class MissionSemanticRetrievalResult(
    val scores: Map<Long, Double>,
    val provider: String,
    val modelVersion: String,
)

fun interface MissionSemanticRetriever {
    fun retrieve(request: MissionSemanticRetrievalRequest): MissionSemanticRetrievalResult
}
