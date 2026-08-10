package backend.yapp.api.mission.generation.dto

import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionItem
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.util.UUID

data class MissionGenerationCreateRequest(
    val category: MissionCategory,
    val item: MissionItem,
    @field:Min(1)
    @field:Max(10)
    val baselineFrequency: Int,
    @field:Min(1)
    @field:Max(2_000_000)
    val baselineAmountWon: Int,
)

data class MissionConfirmRequest(
    @field:Size(min = 1)
    val selectedDraftIds: List<UUID>,
)
