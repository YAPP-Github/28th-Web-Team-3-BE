package backend.yapp.core.goal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * 온보딩 확정 이후의 추적용 목표. 온보딩 완료 시점 값(순자산·월저축·기간·시작시각)을 스냅샷하고,
 * 목표액·기간은 이후 사용자가 수정할 수 있다. 모든 금액 단위는 만원.
 */
@Entity
@Table(name = "goal")
class Goal(
    @Column(name = "guest_user_id", nullable = false, unique = true)
    val guestUserId: Long,
    @Column(name = "target_amount_manwon", nullable = false)
    var targetAmountManwon: Int,
    @Column(name = "period_months", nullable = false)
    var periodMonths: Int,
    @Column(name = "monthly_target_manwon", nullable = false)
    var monthlyTargetManwon: Int,
    @Column(name = "base_amount_manwon", nullable = false)
    var baseAmountManwon: Int,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
