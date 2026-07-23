package backend.yapp.core.mission.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mission_recommendation_snapshot")
class MissionRecommendationSnapshot(
    @Id
    val id: UUID,
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Column(name = "job_id")
    var jobId: UUID? = null,
    @Column(name = "algorithm_version", nullable = false, length = 40)
    val algorithmVersion: String,
    @Column(name = "semantic_provider", nullable = false, length = 40)
    val semanticProvider: String,
    @Column(name = "semantic_model_version", nullable = false, length = 80)
    val semanticModelVersion: String,
    @Column(name = "eligible_candidate_ids", nullable = false, length = 4000)
    val eligibleCandidateIds: String,
    @Column(name = "retrieved_candidate_ids", nullable = false, length = 4000)
    val retrievedCandidateIds: String,
    @Column(name = "weekly_context_snapshot", nullable = false, length = 4000)
    val weeklyContextSnapshot: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

@Entity
@Table(name = "mission_recommendation_candidate")
class MissionRecommendationCandidateTrace(
    @Id
    val id: UUID,
    @Column(name = "snapshot_id", nullable = false)
    val snapshotId: UUID,
    @Column(name = "template_id", nullable = false)
    val templateId: Long,
    @Column(name = "rank_position", nullable = false)
    val rankPosition: Int,
    @Column(name = "raw_score", nullable = false)
    val rawScore: Double,
    @Column(name = "adjusted_score", nullable = false)
    val adjustedScore: Double,
    @Column(name = "retrieved", nullable = false)
    val retrieved: Boolean,
    @Column(name = "exploration_applied", nullable = false)
    val explorationApplied: Boolean = false,
    @Column(name = "applied_penalties", nullable = false, length = 500)
    val appliedPenalties: String,
    @Column(name = "selection_probability")
    val selectionProbability: Double? = null,
    @Column(name = "shown", nullable = false)
    var shown: Boolean = false,
)
