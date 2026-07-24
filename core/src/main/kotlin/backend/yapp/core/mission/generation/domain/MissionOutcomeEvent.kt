package backend.yapp.core.mission.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mission_outcome_event")
class MissionOutcomeEvent(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Column(name = "mission_source", nullable = false, length = 20)
    val missionSource: String,
    @Column(name = "mission_id", nullable = false)
    val missionId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 20)
    val finalStatus: MissionStatus,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
)
