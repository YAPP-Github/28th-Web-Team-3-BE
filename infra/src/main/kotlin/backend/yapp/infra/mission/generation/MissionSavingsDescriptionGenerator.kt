package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.port.MissionDraftCandidate
import backend.yapp.core.mission.generation.port.MissionSavingsCopySource
import backend.yapp.core.mission.generation.port.MissionSavingsDescriptionCopy
import backend.yapp.core.mission.generation.port.MissionSavingsDescriptionGenerator
import backend.yapp.core.mission.generation.port.MissionSavingsDescriptionResult
import java.util.Locale
import tools.jackson.databind.ObjectMapper

class TemplateMissionSavingsDescriptionGenerator(
    private val version: String,
) : MissionSavingsDescriptionGenerator {
    override fun generate(candidates: List<MissionDraftCandidate>): MissionSavingsDescriptionResult =
        MissionSavingsDescriptionResult(candidates.map(::fallback))

    fun fallback(candidate: MissionDraftCandidate): MissionSavingsDescriptionCopy {
        val estimate = candidate.expenseEstimate ?: return MissionSavingsDescriptionCopy(candidate.templateId, null, null, null)
        val description = "${estimate.referenceExpenseLabel} 기준 지출액은 약 ${won(estimate.referenceExpenseWon)}이고 ${estimate.alternativeExpenseLabel}은 약 ${won(estimate.alternativeExpenseWon)}으로 예상돼요. 한 번 바꾸면 약 ${won(estimate.estimatedSavingsPerUnitWon)}, 이번 미션 전체로는 약 ${won(estimate.estimatedSavingsWon)} 절약할 수 있어요."
        return MissionSavingsDescriptionCopy(candidate.templateId, description, MissionSavingsCopySource.TEMPLATE_FALLBACK, version)
    }

    private fun won(value: Int): String = String.format(Locale.KOREA, "%,d원", value)
}

class SpringAiMissionSavingsDescriptionGenerator(
    private val client: MissionSavingsDescriptionAiClient,
    private val objectMapper: ObjectMapper,
    private val prompt: MissionSavingsCopyPromptProperties,
) : MissionSavingsDescriptionGenerator {
    private val fallback = TemplateMissionSavingsDescriptionGenerator(prompt.version)

    override fun generate(candidates: List<MissionDraftCandidate>): MissionSavingsDescriptionResult {
        val eligible = candidates.filter { it.expenseEstimate != null }
        if (eligible.isEmpty()) return MissionSavingsDescriptionResult(candidates.map(fallback::fallback))
        return runCatching {
            val response = client.generate(
                MissionSavingsDescriptionAiRequest(
                    prompt.systemInstruction,
                    "${prompt.userInstruction}\n<prompt-version>${prompt.version}</prompt-version>\n<candidate-data>${objectMapper.writeValueAsString(eligible.map(::input))}</candidate-data>",
                ),
            )
            val byId = validate(response, eligible).associateBy { it.templateId }
            MissionSavingsDescriptionResult(candidates.map { candidate ->
                if (candidate.expenseEstimate == null) fallback.fallback(candidate)
                else MissionSavingsDescriptionCopy(candidate.templateId, byId.getValue(candidate.templateId).savingsDescription, MissionSavingsCopySource.AI, prompt.version)
            })
        }.getOrElse { MissionSavingsDescriptionResult(candidates.map(fallback::fallback)) }
    }

    private fun input(candidate: MissionDraftCandidate): MissionSavingsDescriptionInput {
        val estimate = checkNotNull(candidate.expenseEstimate)
        return MissionSavingsDescriptionInput(candidate.templateId, estimate.referenceExpenseLabel, estimate.alternativeExpenseLabel, won(estimate.referenceExpenseWon), won(estimate.alternativeExpenseWon), won(estimate.estimatedSavingsPerUnitWon), won(estimate.estimatedSavingsWon))
    }

    private fun validate(response: MissionSavingsDescriptionAiResponse, candidates: List<MissionDraftCandidate>): List<MissionSavingsDescriptionAiCopy> {
        require(response.items.size == candidates.size)
        require(response.items.map { it.templateId }.distinct().size == candidates.size)
        val expected = candidates.associateBy { it.templateId }
        require(response.items.map { it.templateId }.toSet() == expected.keys)
        response.items.forEach { item ->
            val estimate = checkNotNull(expected.getValue(item.templateId).expenseEstimate)
            require(item.savingsDescription.length in 1..300 && '\n' !in item.savingsDescription && item.savingsDescription.none(Char::isISOControl))
            require(item.savingsDescription.count { it in ".!?" } in 1..2)
            require(item.savingsDescription.any { it in '가'..'힣' })
            val required = listOf(estimate.referenceExpenseLabel, estimate.alternativeExpenseLabel, won(estimate.referenceExpenseWon), won(estimate.alternativeExpenseWon), won(estimate.estimatedSavingsPerUnitWon), won(estimate.estimatedSavingsWon))
            require(required.all(item.savingsDescription::contains))
            require(Regex("\\d[\\d,]*원").findAll(item.savingsDescription).map { it.value }.all { it in required })
            require(listOf("실제 평균", "내 소비", "반드시", "확실히", "보장", "http://", "https://", "<", "](").none(item.savingsDescription::contains))
        }
        return response.items
    }

    private fun won(value: Int): String = String.format(Locale.KOREA, "%,d원", value)
}

fun interface MissionSavingsDescriptionAiClient { fun generate(request: MissionSavingsDescriptionAiRequest): MissionSavingsDescriptionAiResponse }
data class MissionSavingsDescriptionAiRequest(val systemInstruction: String, val userInstruction: String)
data class MissionSavingsDescriptionAiResponse(val items: List<MissionSavingsDescriptionAiCopy>)
data class MissionSavingsDescriptionAiCopy(val templateId: Long, val savingsDescription: String)
data class MissionSavingsDescriptionInput(val templateId: Long, val referenceExpenseLabel: String, val alternativeExpenseLabel: String, val referenceExpenseText: String, val alternativeExpenseText: String, val estimatedSavingsPerUnitText: String, val estimatedSavingsText: String)
