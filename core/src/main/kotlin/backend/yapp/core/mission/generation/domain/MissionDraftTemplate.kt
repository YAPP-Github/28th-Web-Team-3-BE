package backend.yapp.core.mission.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "mission_draft_template")
class MissionDraftTemplate(
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
    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int,
    @Column(name = "active", nullable = false)
    val active: Boolean = true,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

enum class MissionCategory {
    MEAL,
    TRANSPORT,
    HOBBY,
    LIVING,
}

enum class MissionMetricType {
    COUNT,
    CHECK,
}
