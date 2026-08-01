package backend.yapp.core.goal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 월별 저축액. 게스트·연월(yyyy-MM)당 한 행이며, "현재 저축액 입력" 시 그 달의 값을 덮어쓴다(set).
 * 이번 달 저축액은 현재 연월 행, 총 저축액은 전체 월 합으로 계산한다.
 */
@Entity
@Table(name = "monthly_saving")
class MonthlySaving(
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Column(name = "year_month", nullable = false, length = 7)
    val yearMonth: String,
    @Column(name = "saved_amount_manwon", nullable = false)
    var savedAmountManwon: Int,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
