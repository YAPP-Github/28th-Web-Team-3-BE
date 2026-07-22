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
import java.time.LocalDate

@Entity
@Table(name = "onboarding_profile")
class OnboardingProfile(
    @Column(name = "guest_user_id", nullable = false, unique = true)
    val guestUserId: Long,
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    @Column(name = "monthly_salary_manwon")
    var monthlySalaryManwon: Int? = null,
    @Column(name = "monthly_saving_manwon")
    var monthlySavingManwon: Int? = null,
    @Column(name = "net_worth_manwon")
    var netWorthManwon: Int? = null,
    @Column(name = "goal_period_months")
    var goalPeriodMonths: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OnboardingStatus = OnboardingStatus.IN_PROGRESS,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    fun isReportReady(): Boolean =
        monthlySalaryManwon != null &&
            monthlySavingManwon != null &&
            netWorthManwon != null &&
            goalPeriodMonths != null

    fun isGoalReady(): Boolean =
        monthlySavingManwon != null && goalPeriodMonths != null
}
