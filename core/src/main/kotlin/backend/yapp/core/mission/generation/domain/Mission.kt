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
@Table(name = "mission")
class Mission(
    @Id
    val id: UUID,
    @Column(name = "job_id", nullable = false)
    val jobId: UUID,
    @Column(name = "draft_id", nullable = false, unique = true)
    val draftId: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    val category: MissionCategory,
    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", length = 40)
    val item: MissionItem? = null,
    @Column(name = "title", nullable = false, length = 120)
    val title: String,
    @Column(name = "description", nullable = false, length = 500)
    val description: String,
    @Column(name = "action_code", nullable = false, length = 80)
    val actionCode: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 20)
    val metricType: MissionMetricType,
    @Column(name = "target_count", nullable = false)
    val targetCount: Int,
    @Column(name = "target_unit", nullable = false, length = 40)
    val targetUnit: String,
    @Column(name = "estimated_savings_won", nullable = false)
    val estimatedSavingsWon: Int,
    @Column(name = "savings_estimate_version", nullable = false, length = 40)
    val savingsEstimateVersion: String = "V1",
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

enum class MissionStatus {
    ACTIVE,
    COMPLETED,
    INCOMPLETE,
}
