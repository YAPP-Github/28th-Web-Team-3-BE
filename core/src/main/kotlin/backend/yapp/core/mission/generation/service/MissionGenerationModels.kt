package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import java.time.Instant
import java.util.UUID

data class MissionGenerationJobSnapshot(
    val jobId: UUID,
    val status: MissionGenerationJobStatus,
    val failureCode: String?,
    val generationSource: MissionDraftGenerationSource?,
    val draftsAvailable: Boolean,
    val expiresAt: Instant?,
    val confirmed: Boolean,
)

data class MissionDraftSnapshot(
    val id: UUID,
    val category: MissionCategory,
    val title: String,
    val description: String,
    val actionCode: String,
    val metricType: MissionMetricType,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String,
)

data class MissionSnapshot(
    val id: UUID,
    val category: MissionCategory,
    val title: String,
    val description: String,
    val actionCode: String,
    val metricType: MissionMetricType,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String,
    val status: String,
)

data class MissionGenerationRequestedEvent(val jobId: UUID)
