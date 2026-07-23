package backend.yapp.core.goal.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.goal.domain.Goal
import backend.yapp.core.goal.domain.GoalRepository
import backend.yapp.core.goal.domain.SavingRecord
import backend.yapp.core.goal.domain.SavingRecordRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoalService(
    private val goalRepository: GoalRepository,
    private val savingRecordRepository: SavingRecordRepository,
    private val goalInitializer: GoalInitializer,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun status(guestUserId: Long): GoalStatus = computeStatus(getOrCreateGoal(guestUserId))

    /** "현재 저축액 입력": 입력 금액을 저축 기록으로 추가(누적)한다. 총 저축액과 이번 달 저축액에 함께 반영된다. */
    @Transactional
    fun addSaving(guestUserId: Long, amountManwon: Int): GoalStatus {
        if (amountManwon < MIN_SAVING_MANWON || amountManwon > MAX_SAVING_MANWON) {
            throw BaseException(ErrorCode.INVALID_GOAL_INPUT)
        }
        val goal = getOrCreateGoal(guestUserId)
        savingRecordRepository.save(
            SavingRecord(guestUserId = guestUserId, amountManwon = amountManwon, recordedAt = clock.instant()),
        )
        return computeStatus(goal)
    }

    @Transactional
    fun updateGoal(guestUserId: Long, targetAmountManwon: Int?, periodMonths: Int?): GoalStatus {
        val goal = getOrCreateGoal(guestUserId)
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

    private fun getOrCreateGoal(guestUserId: Long): Goal =
        goalRepository.findByGuestUserId(guestUserId) ?: createOrFind(guestUserId)

    private fun createOrFind(guestUserId: Long): Goal = try {
        goalInitializer.createIfAbsent(guestUserId)
    } catch (_: DataIntegrityViolationException) {
        goalRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun computeStatus(goal: Goal): GoalStatus {
        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        val monthStart = today.withDayOfMonth(1)
        val monthEndExclusive = monthStart.plusMonths(1)
        val lastDayOfMonth = monthEndExclusive.minusDays(1)

        val totalSaved = goal.baseAmountManwon + savingRecordRepository.sumAmountByGuestUserId(goal.guestUserId).toInt()
        val thisMonthSaved = savingRecordRepository.sumAmountInRange(
            guestUserId = goal.guestUserId,
            from = monthStart.atStartOfDay(ZONE).toInstant(),
            to = monthEndExclusive.atStartOfDay(ZONE).toInstant(),
        ).toInt()

        val startedDate = LocalDate.ofInstant(goal.startedAt, ZONE)
        val deadlineDate = startedDate.plusMonths(goal.periodMonths.toLong())

        return GoalStatus(
            targetAmountManwon = goal.targetAmountManwon,
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
        private const val MIN_SAVING_MANWON = 1
        private const val MAX_SAVING_MANWON = 100_000
        private const val MIN_TARGET_MANWON = 1
        private const val MAX_TARGET_MANWON = 1_000_000
        private const val MIN_MONTHS = 3
        private const val MAX_MONTHS = 36
    }
}
