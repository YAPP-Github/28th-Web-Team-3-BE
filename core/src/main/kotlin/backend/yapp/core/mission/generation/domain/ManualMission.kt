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
@Table(name = "manual_mission")
class ManualMission(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    val category: MissionCategory,
    @Column(name = "mission_text", nullable = false, length = 30)
    val missionText: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MissionStatus = MissionStatus.ACTIVE,
    @Column(name = "week_ends_at", nullable = false)
    val weekEndsAt: Instant,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    fun complete(now: Instant): Boolean {
        if (status == MissionStatus.COMPLETED) return false
        check(status == MissionStatus.ACTIVE)
        status = MissionStatus.COMPLETED
        completedAt = now
        return true
    }

    fun markIncomplete(): Boolean {
        if (status == MissionStatus.INCOMPLETE) return false
        check(status == MissionStatus.ACTIVE)
        status = MissionStatus.INCOMPLETE
        return true
    }

    fun softDelete(now: Instant) {
        if (deletedAt == null) deletedAt = now
    }
}
