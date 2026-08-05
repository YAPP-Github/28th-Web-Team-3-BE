package backend.yapp.api.mission.lifecycle.dto

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.service.LifecycleMissionSnapshot
import backend.yapp.core.mission.generation.service.MissionSource
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

data class ManualMissionCreateRequest(
    val category: MissionCategory,
    val text: String,
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val targetCount: Int?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val targetUnit: String?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val estimatedSavingsWon: Int?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val savingsEstimateVersion: String?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val savingsLabel: String?,
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
            savingsLabel = snapshot.estimatedSavingsWon?.let { "약 ${it}원 절약 예상" },
            status = snapshot.status,
            weekEndsAt = snapshot.weekEndsAt,
        )
    }
}
