package backend.yapp.core.onboarding.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "onboarding_goal")
class OnboardingGoal(
    @Column(name = "guest_user_id", nullable = false, unique = true)
    val guestUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 10)
    val plan: GoalPlan,
    @Column(name = "period_months", nullable = false)
    val periodMonths: Int,
    @Column(name = "monthly_saving_manwon", nullable = false)
    val monthlySavingManwon: Int,
    @Column(name = "uplift_permille", nullable = false)
    val upliftPermille: Int,
    @Column(name = "target_amount_manwon", nullable = false)
    val targetAmountManwon: Int,
    @Column(name = "config_version", nullable = false, length = 40)
    val configVersion: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
