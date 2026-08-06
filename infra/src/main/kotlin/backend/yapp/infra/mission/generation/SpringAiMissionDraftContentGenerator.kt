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
import java.time.Duration

class SpringAiMissionDraftContentGenerator(
    private val client: MissionDraftAiClient,
    private val objectMapper: ObjectMapper,
    private val prompt: MissionPromptProperties,
    private val telemetry: MissionDraftGenerationTelemetry = NoopMissionDraftGenerationTelemetry,
    private val rateLimitRetry: MissionDraftRateLimitRetryProperties = MissionDraftRateLimitRetryProperties(),
    private val sleeper: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) },
) : MissionDraftContentGenerator {
    override fun generate(request: MissionDraftContentRequest): MissionDraftContentResult {
        val startedAt = System.nanoTime()
        return try {
            validateCandidateIds(request.candidates)
            if (request.candidates.isEmpty()) {
                return MissionDraftContentResult(
                    copies = emptyList(),
                    source = MissionDraftGenerationSource.AI,
                )
            }
            telemetry.attempted(request.candidates.size)
            val response = generateWithRateLimitRetry(request)
            MissionDraftContentResult(
                copies = validateResponse(response, request.candidates),
                source = MissionDraftGenerationSource.AI,
            )
                .also {
                    telemetry.succeeded(request.candidates.size, elapsedSince(startedAt))
                }
        } catch (error: Throwable) {
            val failure = MissionDraftGenerationFailureClassifier.classify(error)
            telemetry.failed(failure, request.candidates.size, elapsedSince(startedAt))
            telemetry.fallbackUsed(failure, request.candidates.size)
            MissionDraftContentResult(
                copies = fallback(request.candidates),
                source = MissionDraftGenerationSource.TEMPLATE_FALLBACK,
            )
        }
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

    private fun generateWithRateLimitRetry(request: MissionDraftContentRequest): MissionDraftAiResponse {
        val aiRequest = MissionDraftAiRequest(
            systemInstruction = prompt.systemInstruction,
            userInstruction = userInstruction(request.candidates),
        )
        var attempt = 1
        var backoff = rateLimitRetry.initialBackoff
        while (true) {
            try {
                return client.generate(aiRequest)
            } catch (error: Throwable) {
                val failure = MissionDraftGenerationFailureClassifier.classify(error)
                if (
                    failure.category != MissionDraftGenerationFailureCategory.PROVIDER_QUOTA_OR_RATE_LIMIT ||
                    attempt >= rateLimitRetry.maxAttempts
                ) {
                    throw error
                }
                telemetry.retryScheduled(failure)
                sleeper(backoff)
                val doubledBackoff = backoff.multipliedBy(2)
                backoff = if (doubledBackoff > rateLimitRetry.maxBackoff) rateLimitRetry.maxBackoff else doubledBackoff
                attempt++
            }
        }
    }

    private fun validateCandidateIds(candidates: List<MissionDraftCandidate>) {
        if (candidates.map { it.templateId }.distinct().size != candidates.size) {
            throw MissionDraftResponseValidationException(
                MissionDraftValidationRule.CANDIDATE_TEMPLATE_ID_DUPLICATED,
            )
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
        if (copies.size != candidates.size) {
            throw MissionDraftResponseValidationException(
                MissionDraftValidationRule.RESPONSE_ITEM_COUNT_MISMATCH,
            )
        }
        if (copies.map { it.templateId }.distinct().size != copies.size) {
            throw MissionDraftResponseValidationException(
                MissionDraftValidationRule.RESPONSE_TEMPLATE_ID_DUPLICATED,
            )
        }
        if (copies.map { it.templateId }.toSet() != expectedIds) {
            throw MissionDraftResponseValidationException(
                MissionDraftValidationRule.RESPONSE_TEMPLATE_ID_SET_MISMATCH,
            )
        }
        if (copies.any { copy ->
                copy.title.isBlank() ||
                    copy.title.length > MAX_TITLE_LENGTH ||
                    copy.description.isBlank() ||
                    copy.description.length > MAX_DESCRIPTION_LENGTH
            }
        ) {
            throw MissionDraftResponseValidationException(
                MissionDraftValidationRule.RESPONSE_COPY_TEXT_CONSTRAINT_VIOLATED,
            )
        }
        return copies
    }

    private fun elapsedSince(startedAt: Long): Duration = Duration.ofNanos(System.nanoTime() - startedAt)

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
