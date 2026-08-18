package backend.yapp.core.mission.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "mission_weekly_completion")
class MissionWeeklyCompletion(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Column(name = "mission_source", nullable = false, length = 20)
    val missionSource: String,
    @Column(name = "mission_id", nullable = false)
    val missionId: UUID,
    @Column(name = "week_start_date", nullable = false)
    val weekStartDate: LocalDate,
    @Column(name = "completed_at", nullable = false)
    val completedAt: Instant,
)

interface MissionWeeklyCompletionRepository : JpaRepository<MissionWeeklyCompletion, UUID> {
    fun findByMissionSourceAndMissionIdAndWeekStartDate(
        missionSource: String,
        missionId: UUID,
        weekStartDate: LocalDate,
    ): MissionWeeklyCompletion?

    fun findAllByGuestUserIdAndWeekStartDate(
        guestUserId: Long,
        weekStartDate: LocalDate,
    ): List<MissionWeeklyCompletion>

    fun deleteByGuestUserId(guestUserId: Long): Int
}
