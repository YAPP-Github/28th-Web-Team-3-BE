package backend.yapp.core.goal.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.goal.domain.Goal
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.goal.domain.MonthlySaving
import backend.yapp.core.goal.domain.MonthlySavingRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoalService(
    private val goalRepository: GoalRepository,
    private val monthlySavingRepository: MonthlySavingRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun status(guestUserId: Long): GoalStatus = computeStatus(getGoal(guestUserId))

    /**
     * "현재 저축액 입력": 이번 달 저축액을 입력값으로 덮어쓴다(set). 총 저축액은 월별 합으로 재계산된다.
     */
    @Transactional
    fun setThisMonthSaving(guestUserId: Long, savedAmountManwon: Int): GoalStatus {
        val amount = validateRange(savedAmountManwon, MIN_SAVING_MANWON, MAX_SAVING_MANWON)
        val goal = getGoal(guestUserId)
        val yearMonth = currentYearMonth()

        val existing = monthlySavingRepository.findByGuestUserIdAndYearMonth(guestUserId, yearMonth)
        if (existing != null) {
            existing.savedAmountManwon = amount
            existing.updatedAt = clock.instant()
            monthlySavingRepository.save(existing)
        } else {
            monthlySavingRepository.save(
                MonthlySaving(
                    guestUserId = guestUserId,
                    yearMonth = yearMonth,
                    savedAmountManwon = amount,
                    updatedAt = clock.instant(),
                ),
            )
        }
        return computeStatus(goal)
    }

    @Transactional
    fun updateGoal(guestUserId: Long, targetAmountManwon: Int?, periodMonths: Int?): GoalStatus {
        val goal = getGoal(guestUserId)
        targetAmountManwon?.let { goal.targetAmountManwon = validateRange(it, MIN_TARGET_MANWON, MAX_TARGET_MANWON) }
        periodMonths?.let { goal.periodMonths = validateRange(it, MIN_MONTHS, MAX_MONTHS) }
        goal.updatedAt = clock.instant()
        try {
            goalRepository.saveAndFlush(goal)
        } catch (_: OptimisticLockingFailureException) {
            throw BaseException(ErrorCode.GOAL_CONFLICT)
        }
        return computeStatus(goal)
    }

    private fun getGoal(guestUserId: Long): Goal =
        goalRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.GOAL_ONBOARDING_REQUIRED)

    private fun computeStatus(goal: Goal): GoalStatus {
        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        val monthEndExclusive = today.withDayOfMonth(1).plusMonths(1)
        val lastDayOfMonth = monthEndExclusive.minusDays(1)

        val totalSaved = monthlySavingRepository.sumSavedByGuestUserId(goal.guestUserId).toInt()
        val thisMonthSaved = monthlySavingRepository
            .findByGuestUserIdAndYearMonth(goal.guestUserId, currentYearMonth())
            ?.savedAmountManwon ?: 0

        val startedDate = LocalDate.ofInstant(goal.startedAt, ZONE)
        val deadlineDate = startedDate.plusMonths(goal.periodMonths.toLong())

        return GoalStatus(
            targetAmountManwon = goal.targetAmountManwon,
            periodMonths = goal.periodMonths,
            totalSavedManwon = totalSaved,
            progressPercent = cappedPercent(totalSaved, goal.targetAmountManwon),
            usageMonths = ChronoUnit.MONTHS.between(startedDate, today).toInt() + 1,
            deadlineDDay = ChronoUnit.DAYS.between(today, deadlineDate).toInt(),
            thisMonth = ThisMonthStatus(
                targetManwon = goal.monthlyTargetManwon,
                savedManwon = thisMonthSaved,
                progressPercent = cappedPercent(thisMonthSaved, goal.monthlyTargetManwon),
                dDay = ChronoUnit.DAYS.between(today, lastDayOfMonth).toInt(),
            ),
        )
    }

    private fun currentYearMonth(): String = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZONE)).toString()

    /** 진행률 = 값/목표 × 100 (반올림). 금액은 실제값을 쓰되 표시 %는 100으로 캡한다. */
    private fun cappedPercent(value: Int, target: Int): Int {
        if (target <= 0) return 0
        return minOf(100, Math.round(value * 100.0 / target).toInt())
    }

    private fun validateRange(value: Int, min: Int, max: Int): Int {
        if (value < min || value > max) throw BaseException(ErrorCode.INVALID_GOAL_INPUT)
        return value
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MIN_SAVING_MANWON = 0
        private const val MAX_SAVING_MANWON = 100_000
        private const val MIN_TARGET_MANWON = 1
        private const val MAX_TARGET_MANWON = 1_000_000
        private const val MIN_MONTHS = 3
        private const val MAX_MONTHS = 36
    }
}
