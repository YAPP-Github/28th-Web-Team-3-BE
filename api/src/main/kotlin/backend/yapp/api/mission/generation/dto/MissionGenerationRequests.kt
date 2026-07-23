package backend.yapp.api.mission.generation.dto

import jakarta.validation.constraints.Size
import java.util.UUID

data class MissionConfirmRequest(
    @field:Size(min = 1, max = 4)
    val selectedDraftIds: List<UUID>,
)
