package backend.yapp.core.policy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 온통청년 청년정책 API로부터 동기화한 정책(혜택). 원본 정책번호(externalId=plcyNo)로 upsert 한다.
 * 마감/사업종료된 정책은 동기화 시 저장하지 않으며, 신청기간을 알 수 없는 정책은 포함한다.
 */
@Entity
@Table(name = "youth_policy")
class YouthPolicy(
    @Column(name = "external_id", nullable = false, unique = true, length = 30)
    val externalId: String,
    @Column(name = "title", nullable = false, length = 500)
    var title: String,
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
    @Column(name = "support_content", columnDefinition = "TEXT")
    var supportContent: String? = null,
    @Column(name = "large_category", length = 200)
    var largeCategory: String? = null,
    @Column(name = "medium_category", length = 200)
    var mediumCategory: String? = null,
    @Column(name = "category", length = 20)
    var category: String? = null,
    @Column(name = "region_codes", length = 200)
    var regionCodes: String? = null,
    @Column(name = "supervising_org", length = 300)
    var supervisingOrg: String? = null,
    @Column(name = "apply_url", columnDefinition = "TEXT")
    var applyUrl: String? = null,
    @Column(name = "apply_period_text", length = 300)
    var applyPeriodText: String? = null,
    @Column(name = "apply_deadline")
    var applyDeadline: LocalDate? = null,
    @Column(name = "apply_method", columnDefinition = "TEXT")
    var applyMethod: String? = null,
    @Column(name = "submit_documents", columnDefinition = "TEXT")
    var submitDocuments: String? = null,
    @Column(name = "target_min_age")
    var targetMinAge: Int? = null,
    @Column(name = "target_max_age")
    var targetMaxAge: Int? = null,
    @Column(name = "earn_condition", columnDefinition = "TEXT")
    var earnCondition: String? = null,
    @Column(name = "additional_qualification", columnDefinition = "TEXT")
    var additionalQualification: String? = null,
    @Column(name = "external_modified_at", length = 30)
    var externalModifiedAt: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
