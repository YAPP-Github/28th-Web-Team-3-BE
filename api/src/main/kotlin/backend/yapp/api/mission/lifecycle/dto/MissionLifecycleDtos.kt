package backend.yapp.api.mission.lifecycle.dto

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.service.LifecycleMissionSnapshot
import backend.yapp.core.mission.generation.service.MissionProgressSnapshot
import backend.yapp.core.mission.generation.service.MissionSource
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

data class ManualMissionCreateRequest(
    val category: MissionCategory,
    val text: String,
)

data class MissionCatalogResponse(
    val categories: List<MissionCategoryCatalogResponse>,
) {
    companion object {
        fun create() = MissionCatalogResponse(
            listOf(MissionCategory.MEAL, MissionCategory.LIVING, MissionCategory.HOBBY).map { category ->
                MissionCategoryCatalogResponse(
                    category = category,
                    items = MissionItem.entries.filter { it.category == category && it.active }
                        .map { MissionItemResponse(it, it.label) },
                )
            },
        )
    }
}

data class MissionCategoryCatalogResponse(
    val category: MissionCategory,
    val items: List<MissionItemResponse>,
)

data class MissionItemResponse(
    val code: MissionItem,
    val label: String,
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val item: MissionItem?,
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val savingsDisclaimer: String?,
    val status: MissionStatus,
    val weekEndsAt: Instant,
) {
    companion object {
        fun from(snapshot: LifecycleMissionSnapshot) = MissionLifecycleResponse(
            id = snapshot.id,
            source = snapshot.source,
            category = snapshot.category,
            item = snapshot.item,
            title = snapshot.title,
            targetCount = snapshot.targetCount,
            targetUnit = snapshot.targetUnit,
            estimatedSavingsWon = snapshot.estimatedSavingsWon,
            savingsEstimateVersion = snapshot.savingsEstimateVersion,
            savingsLabel = snapshot.estimatedSavingsWon?.let { savings ->
                if (snapshot.status == MissionStatus.COMPLETED) {
                    "완료됨 · 평소보다 ${snapshot.item?.label ?: "항목"}비 ${savings}원 아꼈어요"
                } else {
                    "미션을 완료하면 평소보다 ${snapshot.item?.label ?: "항목"}비를 ${savings}원 아낄 수 있어요"
                }
            },
            savingsDisclaimer = snapshot.estimatedSavingsWon?.let { "단순 추정치로 정확하지 않을 수 있어요" },
            status = snapshot.status,
            weekEndsAt = snapshot.weekEndsAt,
        )
    }
}

data class MissionProgressResponse(
    val completedCount: Int,
    val totalCount: Int,
    val progressPercent: Int,
    val weekStartDate: java.time.LocalDate,
) {
    companion object {
        fun from(snapshot: MissionProgressSnapshot) = MissionProgressResponse(
            completedCount = snapshot.completedCount,
            totalCount = snapshot.totalCount,
            progressPercent = snapshot.progressPercent,
            weekStartDate = snapshot.weekStartDate,
        )
    }
}
