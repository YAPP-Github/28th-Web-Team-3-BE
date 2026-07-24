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
@Table(name = "mission_draft")
class MissionDraft(
    @Id
    val id: UUID,
    @Column(name = "job_id", nullable = false)
    val jobId: UUID,
    @Column(name = "template_id", nullable = false)
    val templateId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    val category: MissionCategory,
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
    @Column(name = "reference_expense_label", length = 80)
    val referenceExpenseLabel: String? = null,
    @Column(name = "alternative_expense_label", length = 80)
    val alternativeExpenseLabel: String? = null,
    @Column(name = "reference_expense_won")
    val referenceExpenseWon: Int? = null,
    @Column(name = "alternative_expense_won")
    val alternativeExpenseWon: Int? = null,
    @Column(name = "estimated_savings_per_unit_won")
    val estimatedSavingsPerUnitWon: Int? = null,
    @Column(name = "expense_unit", length = 20)
    val expenseUnit: String? = null,
    @Column(name = "estimate_basis", length = 40)
    val estimateBasis: String? = null,
    @Column(name = "savings_description", length = 300)
    val savingsDescription: String? = null,
    @Column(name = "savings_copy_source", length = 30)
    val savingsCopySource: String? = null,
    @Column(name = "savings_copy_version", length = 40)
    val savingsCopyVersion: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
