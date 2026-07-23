package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionDraftContentGenerator
import backend.yapp.core.mission.generation.port.MissionDraftContentRequest
import backend.yapp.core.mission.generation.port.MissionDraftContentResult
import backend.yapp.core.mission.generation.port.MissionDraftCopy
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import tools.jackson.databind.ObjectMapper

class OpenAiMissionDraftContentGenerator(
    private val client: OpenAiResponsesClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
) : MissionDraftContentGenerator {
    override fun generate(request: MissionDraftContentRequest): MissionDraftContentResult =
        runCatching {
            require(properties.safetySalt.isNotBlank()) {
                "OPENAI_SAFETY_SALT is required for the openai provider"
            }
            val response = client.create(requestBody(request))
            MissionDraftContentResult(
                copies = parseAndValidate(response, request.candidates),
                source = MissionDraftGenerationSource.OPENAI,
            )
        }.getOrElse {
            MissionDraftContentResult(
                copies = fallback(request.candidates),
                source = MissionDraftGenerationSource.TEMPLATE_FALLBACK,
            )
        }

    private fun requestBody(request: MissionDraftContentRequest): String {
        val candidateInput = request.candidates.map { candidate ->
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
        val schema = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "items" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "properties" to mapOf(
                            "templateId" to mapOf("type" to "integer"),
                            "title" to mapOf("type" to "string", "maxLength" to MAX_TITLE_LENGTH),
                            "description" to mapOf("type" to "string", "maxLength" to MAX_DESCRIPTION_LENGTH),
                        ),
                        "required" to listOf("templateId", "title", "description"),
                    ),
                ),
            ),
            "required" to listOf("items"),
        )
        val inputJson = objectMapper.writeValueAsString(candidateInput)
        val body = mapOf(
            "model" to properties.model,
            "reasoning" to mapOf("effort" to properties.reasoningEffort),
            "max_output_tokens" to properties.maxOutputTokens,
            "safety_identifier" to safetyIdentifier(request.guestUserId),
            "input" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_text",
                            "text" to SYSTEM_PROMPT,
                        ),
                    ),
                ),
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_text",
                            "text" to inputJson,
                        ),
                    ),
                ),
            ),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "mission_draft_copy",
                    "strict" to true,
                    "schema" to schema,
                ),
            ),
        )
        return objectMapper.writeValueAsString(body)
    }

    private fun parseAndValidate(
        responseBody: String,
        candidates: List<MissionDraftCandidate>,
    ): List<MissionDraftCopy> {
        val response = objectMapper.readTree(responseBody)
        val outputText = response.path("output")
            .flatMap { output -> output.path("content").toList() }
            .firstOrNull { content -> content.path("type").asString() == "output_text" }
            ?.path("text")
            ?.asString()
            ?: error("OpenAI response did not contain output_text")
        val items = objectMapper.readTree(outputText).path("items")
        require(items.isArray) { "OpenAI structured output did not contain items" }

        val copies = items.toList().map { item ->
            MissionDraftCopy(
                templateId = item.path("templateId").asLong(),
                title = item.path("title").asString(),
                description = item.path("description").asString(),
            )
        }
        val expectedIds = candidates.map { it.templateId }.toSet()
        require(copies.size == candidates.size)
        require(copies.map { it.templateId }.toSet() == expectedIds)
        require(copies.all { copy ->
            copy.title.isNotBlank() &&
                copy.title.length <= MAX_TITLE_LENGTH &&
                copy.description.isNotBlank() &&
                copy.description.length <= MAX_DESCRIPTION_LENGTH
        })
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

    private fun safetyIdentifier(guestUserId: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.safetySalt.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(guestUserId.toString().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val SYSTEM_PROMPT =
            "주어진 미션의 구조화 값과 templateId는 절대 변경하지 마세요. " +
                "각 항목의 title과 description만 자연스럽고 간결한 한국어로 다듬고 모든 항목을 한 번씩 반환하세요."
    }
}
