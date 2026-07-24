package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.domain.ManualMission
import backend.yapp.core.mission.generation.domain.ManualMissionRepository
import backend.yapp.core.mission.generation.domain.Mission
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionStatus
import backend.yapp.core.mission.generation.domain.MissionOutcomeEvent
import backend.yapp.core.mission.generation.domain.MissionOutcomeEventRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MissionLifecycleService(
    private val missionRepository: MissionRepository,
    private val manualRepository: ManualMissionRepository,
    private val outcomeRepository: MissionOutcomeEventRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(guestUserId: Long, status: MissionStatus?): List<LifecycleMissionSnapshot> =
        (
            missionRepository.findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId).map { it.toSnapshot() } +
                manualRepository.findAllByGuestUserIdOrderByCreatedAtDesc(guestUserId).map { it.toSnapshot() }
            ).filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }

    @Transactional
    fun createManual(
        guestUserId: Long,
        category: MissionCategory,
        text: String,
        targetCount: Int,
        targetUnit: String,
    ): LifecycleMissionSnapshot {
        if (text.isBlank() || text.length > 500 || targetCount !in 1..100 || targetUnit.isBlank()) {
            throw BaseException(ErrorCode.MANUAL_MISSION_INVALID)
        }
        val now = clock.instant()
        return manualRepository.save(
            ManualMission(
                id = UUID.randomUUID(),
                guestUserId = guestUserId,
                category = category,
                missionText = text.trim(),
                structuredTags = tags(text, category),
                targetCount = targetCount,
                targetUnit = targetUnit.take(40),
                weekEndsAt = weekEnd(now),
                createdAt = now,
            ),
        ).toSnapshot()
    }

    @Transactional
    fun deleteRecommended(guestUserId: Long, missionId: UUID) {
        val mission = missionRepository.findByIdAndGuestUserId(missionId, guestUserId)
            ?: throw BaseException(ErrorCode.MISSION_NOT_FOUND)
        missionRepository.delete(mission)
    }

    @Transactional
    fun complete(guestUserId: Long, source: MissionSource, missionId: UUID): LifecycleMissionSnapshot {
        val now = clock.instant()
        return when (source) {
            MissionSource.RECOMMENDED -> {
                val mission = missionRepository.findByIdAndGuestUserId(missionId, guestUserId)
                    ?: throw BaseException(ErrorCode.MISSION_NOT_FOUND)
                complete(mission.status) {
                    if (mission.complete(now)) {
                        recordOutcome(
                            guestUserId,
                            MissionSource.RECOMMENDED,
                            mission.id,
                            MissionStatus.COMPLETED,
                            now,
                        )
                    }
                }
                mission.toSnapshot()
            }
            MissionSource.MANUAL -> {
                val mission = manualRepository.findByIdAndGuestUserId(missionId, guestUserId)
                    ?: throw BaseException(ErrorCode.MISSION_NOT_FOUND)
                complete(mission.status) {
                    if (mission.complete(now)) {
                        recordOutcome(
                            guestUserId,
                            MissionSource.MANUAL,
                            mission.id,
                            MissionStatus.COMPLETED,
                            now,
                        )
                    }
                }
                mission.toSnapshot()
            }
        }
    }

    @Transactional
    fun markOverdueIncomplete(): Int = markOverdueIncomplete(clock.instant())

    @Transactional
    fun markOverdueIncomplete(now: Instant): Int {
        val recommended = missionRepository.findAllByStatusAndWeekEndsAtLessThanEqual(MissionStatus.ACTIVE, now)
        val manual = manualRepository.findAllByStatusAndWeekEndsAtLessThanEqual(MissionStatus.ACTIVE, now)
        recommended.forEach { mission ->
            if (mission.markIncomplete()) {
                recordOutcome(
                    mission.guestUserId,
                    MissionSource.RECOMMENDED,
                    mission.id,
                    MissionStatus.INCOMPLETE,
                    now,
                )
            }
        }
        manual.forEach { mission ->
            if (mission.markIncomplete()) {
                recordOutcome(
                    mission.guestUserId,
                    MissionSource.MANUAL,
                    mission.id,
                    MissionStatus.INCOMPLETE,
                    now,
                )
            }
        }
        return recommended.size + manual.size
    }

    private fun complete(status: MissionStatus, action: () -> Unit) {
        if (status == MissionStatus.INCOMPLETE) throw BaseException(ErrorCode.MISSION_STATUS_CONFLICT)
        action()
    }

    private fun weekEnd(now: Instant): Instant {
        val zone = ZoneId.of("Asia/Seoul")
        val date = now.atZone(zone).toLocalDate()
        val nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return nextMonday.atStartOfDay(zone).toInstant()
    }

    private fun tags(text: String, category: MissionCategory): String {
        val normalized = text.lowercase()
        val detected = SAFE_TAG_KEYWORDS
            .filterValues { keywords -> keywords.any(normalized::contains) }
            .keys
        return (setOf(category.name) + detected).joinToString(",")
    }

    private fun recordOutcome(
        guestUserId: Long,
        source: MissionSource,
        missionId: UUID,
        finalStatus: MissionStatus,
        now: Instant,
    ) {
        outcomeRepository.save(
            MissionOutcomeEvent(
                id = UUID.randomUUID(),
                guestUserId = guestUserId,
                missionSource = source.name,
                missionId = missionId,
                finalStatus = finalStatus,
                occurredAt = now,
            ),
        )
    }

    private fun Mission.toSnapshot() = LifecycleMissionSnapshot(
        id, MissionSource.RECOMMENDED, category, title, targetCount, targetUnit,
        estimatedSavingsWon, savingsEstimateVersion, status, weekEndsAt, createdAt,
    )

    private fun ManualMission.toSnapshot() = LifecycleMissionSnapshot(
        id, MissionSource.MANUAL, category, missionText, targetCount, targetUnit,
        0, "NOT_ESTIMATED", status, weekEndsAt, createdAt,
    )

    companion object {
        private val SAFE_TAG_KEYWORDS = mapOf(
            "DELIVERY" to setOf("배달"),
            "DINING_OUT" to setOf("외식"),
            "PAID_BEVERAGE" to setOf("카페", "음료"),
            "TAXI" to setOf("택시"),
            "PUBLIC_TRANSIT" to setOf("버스", "지하철", "대중교통"),
            "SHOPPING" to setOf("쇼핑", "구매"),
            "SUBSCRIPTION" to setOf("구독"),
            "HOBBY" to setOf("취미", "게임", "공연"),
            "INVENTORY" to setOf("재고", "냉장고"),
        )
    }
}

enum class MissionSource {
    RECOMMENDED,
    MANUAL,
}

data class LifecycleMissionSnapshot(
    val id: UUID,
    val source: MissionSource,
    val category: MissionCategory,
    val title: String,
    val targetCount: Int,
    val targetUnit: String,
    val estimatedSavingsWon: Int,
    val savingsEstimateVersion: String,
    val status: MissionStatus,
    val weekEndsAt: Instant,
    val createdAt: Instant,
)
