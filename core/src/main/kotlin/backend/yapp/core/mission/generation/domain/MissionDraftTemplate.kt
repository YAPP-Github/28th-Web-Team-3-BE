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
    @Column(name = "target_code", nullable = false, length = 80)
    val targetCode: String = "GENERAL",
    @Column(name = "eligible_codes", nullable = false, length = 500)
    val eligibleCodes: String = "",
    @Column(name = "excluded_codes", nullable = false, length = 500)
    val excludedCodes: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "target_formula", nullable = false, length = 30)
    val targetFormula: MissionTargetFormula = MissionTargetFormula.FIXED,
    @Column(name = "cooldown_family", nullable = false, length = 80)
    val cooldownFamily: String = actionCode,
    @Column(name = "verification_type", nullable = false, length = 40)
    val verificationType: String = "SELF_REPORT",
    @Column(name = "average_savings_per_unit", nullable = false)
    val averageSavingsPerUnit: Int = estimatedSavingsWon,
    @Column(name = "savings_estimate_version", nullable = false, length = 40)
    val savingsEstimateVersion: String = "V1",
    @Column(name = "embedding_text", nullable = false, length = 1000)
    val embeddingText: String = "$title $description",
    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int,
    @Column(name = "active", nullable = false)
    val active: Boolean = true,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

enum class MissionCategory {
    MEAL,
    @Deprecated("Transport missions were removed by policy #78")
    TRANSPORT,
    HOBBY,
    LIVING,
    ;

    val active: Boolean
        get() = this != TRANSPORT
}

enum class MissionItem(
    val category: MissionCategory,
    val label: String,
    val active: Boolean = true,
) {
    DELIVERY_FOOD(MissionCategory.MEAL, "배달음식"),
    DINING_OUT(MissionCategory.MEAL, "외식"),
    DRINKING(MissionCategory.MEAL, "술자리", active = false),
    CAFE(MissionCategory.MEAL, "카페"),
    SNACK(MissionCategory.MEAL, "간식", active = false),
    CONVENIENCE_STORE(MissionCategory.MEAL, "편의점"),
    CLOTHING(MissionCategory.LIVING, "의류"),
    COSMETICS(MissionCategory.LIVING, "화장품"),
    HOUSEHOLD_GOODS(MissionCategory.LIVING, "생활용품"),
    BEAUTY(MissionCategory.LIVING, "미용"),
    SELF_DEVELOPMENT(MissionCategory.LIVING, "자기계발", active = false),
    HOBBY_GOODS(MissionCategory.HOBBY, "용품&굿즈", active = false),
    GAME(MissionCategory.HOBBY, "게임", active = false),
    DIGITAL_CONTENT(MissionCategory.HOBBY, "디지털 콘텐츠", active = false),
    CLASS(MissionCategory.HOBBY, "수업&클래스"),
    PERFORMANCE_TICKET(MissionCategory.HOBBY, "공연&전시&티켓"),
    CLUB_GATHERING(MissionCategory.HOBBY, "동호회&모임", active = false),
    EQUIPMENT_RENTAL(MissionCategory.HOBBY, "장비 대여", active = false),
    SPACE_USE(MissionCategory.HOBBY, "공간 이용", active = false),
}

enum class MissionMetricType {
    COUNT,
    CHECK,
}

enum class MissionTargetFormula {
    REDUCE_MAX,
    REPLACE,
    FIXED,
    CHECK,
    RECORD,
}
