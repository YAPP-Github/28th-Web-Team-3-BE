package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionDraftContentResult
import backend.yapp.core.mission.generation.port.MissionDraftCopy
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class SpringAiMissionDraftContentGenerator(
    private val client: MissionDraftAiClient,
    private val objectMapper: ObjectMapper,
    private val prompt: MissionPromptProperties,
) : MissionDraftContentGenerator {
    override fun generate(request: MissionDraftContentRequest): MissionDraftContentResult =
        runCatching {
            validateCandidateIds(request.candidates)
            if (request.candidates.isEmpty()) {
                return@runCatching MissionDraftContentResult(
                    copies = emptyList(),
                    source = MissionDraftGenerationSource.OPENAI,
                )
            }
            val response = client.generate(
                MissionDraftAiRequest(
                    systemInstruction = prompt.systemInstruction,
                    userInstruction = userInstruction(request.candidates),
                ),
            )
            MissionDraftContentResult(
                copies = validateResponse(response, request.candidates),
                source = MissionDraftGenerationSource.OPENAI,
            )
        }.getOrElse {
            MissionDraftContentResult(
                copies = fallback(request.candidates),
                source = MissionDraftGenerationSource.TEMPLATE_FALLBACK,
            )
        }

    private fun userInstruction(candidates: List<MissionDraftCandidate>): String {
        val candidateInput = candidates.map { candidate ->
            mapOf(
                "templateId" to candidate.templateId,
                "category" to candidate.category.name,
                "templateTitle" to candidate.templateTitle,
                "templateDescription" to candidate.templateDescription,
                "actionCode" to candidate.actionCode,
                "metricType" to candidate.metricType.name,
                "targetCount" to candidate.targetCount,
                "targetUnit" to candidate.targetUnit,
            )
        }
        return buildString {
            appendLine(prompt.userInstruction)
            appendLine()
            appendLine("<prompt-version>${prompt.version}</prompt-version>")
            appendLine("<candidate-data>")
            appendLine(objectMapper.writeValueAsString(candidateInput))
            append("</candidate-data>")
        }
    }

    private fun validateCandidateIds(candidates: List<MissionDraftCandidate>) {
        require(candidates.map { it.templateId }.distinct().size == candidates.size) {
            "Candidate templateId values must be unique"
        }
    }

    private fun validateResponse(
        response: MissionDraftAiResponse,
        candidates: List<MissionDraftCandidate>,
    ): List<MissionDraftCopy> {
        val expectedIds = candidates.map { it.templateId }.toSet()
        val copies = response.items.map { item ->
            MissionDraftCopy(
                templateId = item.templateId,
                title = item.title,
                description = item.description,
            )
        }
        require(copies.size == candidates.size) { "AI response item count changed" }
        require(copies.map { it.templateId }.distinct().size == copies.size) {
            "AI response templateId values must be unique"
        }
        require(copies.map { it.templateId }.toSet() == expectedIds) {
            "AI response templateId set changed"
        }
        require(copies.all { copy ->
            copy.title.isNotBlank() &&
                copy.title.length <= MAX_TITLE_LENGTH &&
                copy.description.isNotBlank() &&
                copy.description.length <= MAX_DESCRIPTION_LENGTH
        }) {
            "AI response copy violated text constraints"
        }
        return copies
    }

    private fun fallback(candidates: List<MissionDraftCandidate>): List<MissionDraftCopy> =
        candidates.map { candidate ->
            MissionDraftCopy(
                templateId = candidate.templateId,
                title = candidate.templateTitle,
                description = candidate.templateDescription,
            )
        }

    companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}

fun interface MissionDraftAiClient {
    fun generate(request: MissionDraftAiRequest): MissionDraftAiResponse
}

data class MissionDraftAiRequest(
    val systemInstruction: String,
    val userInstruction: String,
)

data class MissionDraftAiResponse(
    val items: List<MissionDraftAiCopy>,
)

data class MissionDraftAiCopy(
    val templateId: Long,
    val title: String,
    val description: String,
)

class ChatClientMissionDraftAiClient(
    private val chatClient: ChatClient,
) : MissionDraftAiClient {
    private val converter = BeanOutputConverter(
        MissionDraftAiResponse::class.java,
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build(),
    )

    override fun generate(request: MissionDraftAiRequest): MissionDraftAiResponse =
        checkNotNull(
            chatClient.prompt()
                .system(request.systemInstruction)
                .user(request.userInstruction)
                .call()
                .entity(converter) { spec ->
                    spec.useProviderStructuredOutput().validateSchema()
                },
        ) {
            "Spring AI returned an empty structured response"
        }
}
