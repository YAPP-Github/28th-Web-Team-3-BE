package backend.yapp.core.goal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 저축 기록. "현재 저축액 입력"마다 한 건씩 쌓이는 append-only 이벤트로, 이번 달 저축액·총 저축액을 합산으로 계산한다.
 */
@Entity
@Table(name = "saving_record")
class SavingRecord(
    @Column(name = "guest_user_id", nullable = false)
    val guestUserId: Long,
    @Column(name = "amount_manwon", nullable = false)
    val amountManwon: Int,
    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
