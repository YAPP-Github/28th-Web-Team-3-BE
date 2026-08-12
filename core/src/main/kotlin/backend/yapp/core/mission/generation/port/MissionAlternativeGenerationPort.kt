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
    fun search(query: String, count: Int): MissionBlogSearchOutcome
}

sealed interface MissionBlogSearchOutcome {
    val category: MissionBlogSearchOutcomeCategory

    data class Completed(
        override val category: MissionBlogSearchOutcomeCategory,
        val providerItemCount: Int,
        val results: List<MissionBlogSearchResult>,
    ) : MissionBlogSearchOutcome {
        init {
            require(category in COMPLETED_CATEGORIES)
        }
    }

    data class Failed(
        override val category: MissionBlogSearchOutcomeCategory,
        val attempts: Int,
    ) : MissionBlogSearchOutcome {
        init {
            require(category !in COMPLETED_CATEGORIES)
        }
    }

    companion object {
        private val COMPLETED_CATEGORIES = setOf(
            MissionBlogSearchOutcomeCategory.SUCCESS,
            MissionBlogSearchOutcomeCategory.EMPTY_PROVIDER_RESULT,
            MissionBlogSearchOutcomeCategory.ALL_NORMALIZED_OUT,
        )
    }
}

enum class MissionBlogSearchOutcomeCategory {
    SUCCESS,
    CREDENTIALS_MISSING,
    AUTHORIZATION,
    RATE_LIMIT,
    HTTP_4XX_OTHER,
    HTTP_5XX,
    NETWORK_TIMEOUT,
    RESPONSE_DESERIALIZATION,
    EMPTY_PROVIDER_RESULT,
    ALL_NORMALIZED_OUT,
    UNEXPECTED_INTERNAL,
}

data class MissionBlogSearchResult(
    val title: String,
    val description: String,
    val source: String,
    val url: String,
)
