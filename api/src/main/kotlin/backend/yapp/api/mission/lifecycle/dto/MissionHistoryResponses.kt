package backend.yapp.api.mission.lifecycle.dto

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.mission.generation.service.MissionWeeklyHistorySnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

data class MissionHistoriesResponse(
    val histories: List<MissionWeeklyHistoryResponse>,
) {
    companion object {
        fun from(snapshots: List<MissionWeeklyHistorySnapshot>) =
            MissionHistoriesResponse(snapshots.map(MissionWeeklyHistoryResponse::from))
    }
}

data class MissionWeeklyHistoryResponse(
    @get:Schema(description = "목요일 귀속 규칙으로 계산한 월 내 주차", example = "1")
    val weekOfMonth: Int,
    @get:Schema(description = "주차 시작일(월요일, Asia/Seoul)", example = "2026-08-03")
    val weekStartDate: LocalDate,
    @get:Schema(description = "주차 종료일(일요일, Asia/Seoul)", example = "2026-08-09")
    val weekEndDate: LocalDate,
    @get:Schema(description = "해당 주차에 완료한 미션 수", example = "1")
    val completedCount: Int,
    @get:Schema(description = "해당 주차의 전체 미션 수", example = "10")
    val totalCount: Int,
    @get:Schema(description = "서버 시간 기준 현재 진행 중인 주차 여부", example = "false")
    val isCurrentWeek: Boolean,
) {
    companion object {
        fun from(snapshot: MissionWeeklyHistorySnapshot) = MissionWeeklyHistoryResponse(
            weekOfMonth = snapshot.weekOfMonth,
            weekStartDate = snapshot.weekStartDate,
            weekEndDate = snapshot.weekEndDate,
            completedCount = snapshot.completedCount,
            totalCount = snapshot.totalCount,
            isCurrentWeek = snapshot.isCurrentWeek,
        )
    }
}

object MissionHistoryPeriodParser {
    fun parse(year: String?, month: String?): YearMonth {
        val parsedYear = year?.toIntOrNull() ?: invalidPeriod()
        val parsedMonth = month?.toIntOrNull() ?: invalidPeriod()
        return try {
            YearMonth.of(parsedYear, parsedMonth)
        } catch (_: DateTimeException) {
            invalidPeriod()
        }
    }

    private fun invalidPeriod(): Nothing = throw BaseException(ErrorCode.MISSION_HISTORY_INVALID_PERIOD)
}
