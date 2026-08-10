package backend.yapp.core.mission.generation.port

import backend.yapp.core.mission.generation.domain.MissionItem

interface MissionAlternativeGenerationPort {
    fun generate(request: MissionAlternativeGenerationRequest): MissionAlternativeGenerationResult
}

data class MissionAlternativeGenerationRequest(
    val item: MissionItem,
    val blogContexts: List<MissionBlogSearchResult>,
)

data class MissionAlternativeTemplate(
    val titleTemplate: String,
    val description: String,
)

data class MissionAlternativeGenerationResult(
    val alternatives: List<MissionAlternativeTemplate>,
    val source: MissionDraftGenerationSource,
)

interface MissionBlogSearchPort {
    fun search(query: String, count: Int): List<MissionBlogSearchResult>
}

data class MissionBlogSearchResult(
    val title: String,
    val description: String,
    val source: String,
    val url: String,
)
