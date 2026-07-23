package backend.yapp.api.mission.generation.dto

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.mission.generation.service.MissionDraftSnapshot
import backend.yapp.core.mission.generation.service.MissionGenerationJobSnapshot
import backend.yapp.core.mission.generation.service.MissionSnapshot
import java.time.Instant
import java.util.UUID

data class MissionGenerationJobResponse(
    val jobId: UUID,
    val status: MissionGenerationJobStatus,
    val failureCode: String?,
    val generationSource: MissionDraftGenerationSource?,
    val draftsAvailable: Boolean,
    val expiresAt: Instant?,
    val confirmed: Boolean,
    val pollingIntervalMillis: Long,
) {
    companion object {
        fun from(snapshot: MissionGenerationJobSnapshot): MissionGenerationJobResponse =
            MissionGenerationJobResponse(
                jobId = snapshot.jobId,
                status = snapshot.status,
                failureCode = snapshot.failureCode,
                generationSource = snapshot.generationSource,
                draftsAvailable = snapshot.draftsAvailable,
                expiresAt = snapshot.expiresAt,
                confirmed = snapshot.confirmed,
                pollingIntervalMillis = 2_000,
            )
    }
}

data class MissionDraftsResponse(
    val jobId: UUID,
    val categories: List<MissionCategoryDraftsResponse>,
) {
    companion object {
        fun from(jobId: UUID, drafts: List<MissionDraftSnapshot>): MissionDraftsResponse =
            MissionDraftsResponse(
                jobId = jobId,
                categories = drafts.groupBy { it.category }
                    .map { (category, categoryDrafts) ->
                        MissionCategoryDraftsResponse(
                            category = category,
                            drafts = categoryDrafts.map(MissionDraftResponse::from),
                        )
                    },
            )
    }
}

data class MissionCategoryDraftsResponse(
    val category: MissionCategory,
    val drafts: List<MissionDraftResponse>,
)

data class MissionDraftResponse(
    val id: UUID,
    val title: String,
    val description: String,
    val actionCode: String,
    val metricType: String,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsLabel: String,
) {
    companion object {
        fun from(snapshot: MissionDraftSnapshot): MissionDraftResponse =
            MissionDraftResponse(
                id = snapshot.id,
                title = snapshot.title,
                description = snapshot.description,
                actionCode = snapshot.actionCode,
                metricType = snapshot.metricType.name,
                targetCount = snapshot.targetCount,
                targetUnit = snapshot.targetUnit,
                estimatedSavingsWon = snapshot.estimatedSavingsWon,
                savingsLabel = "약 ${snapshot.estimatedSavingsWon}원 절약 예상",
            )
    }
}

data class MissionConfirmResponse(
    val jobId: UUID,
    val missions: List<MissionResponse>,
) {
    companion object {
        fun from(jobId: UUID, missions: List<MissionSnapshot>): MissionConfirmResponse =
            MissionConfirmResponse(jobId, missions.map(MissionResponse::from))
    }
}

data class MissionResponse(
    val id: UUID,
    val category: MissionCategory,
    val title: String,
    val description: String,
    val actionCode: String,
    val metricType: String,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsLabel: String,
    val status: String,
) {
    companion object {
        fun from(snapshot: MissionSnapshot): MissionResponse =
            MissionResponse(
                id = snapshot.id,
                category = snapshot.category,
                title = snapshot.title,
                description = snapshot.description,
                actionCode = snapshot.actionCode,
                metricType = snapshot.metricType.name,
                targetCount = snapshot.targetCount,
                targetUnit = snapshot.targetUnit,
                estimatedSavingsWon = snapshot.estimatedSavingsWon,
                savingsLabel = "약 ${snapshot.estimatedSavingsWon}원 절약 예상",
                status = snapshot.status,
            )
    }
}
