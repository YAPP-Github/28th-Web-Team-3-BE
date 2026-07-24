package backend.yapp.api.mission.lifecycle.dto

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.service.LifecycleMissionSnapshot
import backend.yapp.core.mission.generation.service.MissionSource
import backend.yapp.core.mission.generation.port.MissionExpenseEstimate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ManualMissionCreateRequest(
    val category: MissionCategory,
    @field:NotBlank @field:Size(max = 500)
    val text: String,
    @field:Min(1) @field:Max(100)
    val targetCount: Int,
    @field:NotBlank @field:Size(max = 40)
    val targetUnit: String,
)

data class MissionsResponse(val missions: List<MissionLifecycleResponse>) {
    companion object {
        fun from(snapshots: List<LifecycleMissionSnapshot>) =
            MissionsResponse(snapshots.map(MissionLifecycleResponse::from))
    }
}

data class MissionLifecycleResponse(
    val id: UUID,
    val source: MissionSource,
    val category: MissionCategory,
    val title: String,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String,
    val expenseEstimate: MissionExpenseEstimate?,
    val savingsDescription: String?,
    val savingsCopyVersion: String?,
    val savingsLabel: String,
    val status: MissionStatus,
    val weekEndsAt: Instant,
) {
    companion object {
        fun from(snapshot: LifecycleMissionSnapshot) = MissionLifecycleResponse(
            id = snapshot.id,
            source = snapshot.source,
            category = snapshot.category,
            title = snapshot.title,
            targetCount = snapshot.targetCount,
            targetUnit = snapshot.targetUnit,
            estimatedSavingsWon = snapshot.estimatedSavingsWon,
            savingsEstimateVersion = snapshot.savingsEstimateVersion,
            expenseEstimate = snapshot.expenseEstimate,
            savingsDescription = snapshot.savingsDescription,
            savingsCopyVersion = snapshot.savingsCopyVersion,
            savingsLabel = if (snapshot.savingsEstimateVersion == "NOT_ESTIMATED") {
                "예상 절약액 미산정"
            } else {
                "약 ${snapshot.estimatedSavingsWon}원 절약 예상"
            },
            status = snapshot.status,
            weekEndsAt = snapshot.weekEndsAt,
        )
    }
}
