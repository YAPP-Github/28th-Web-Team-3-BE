package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import java.time.Period

object MissionSearchQueryFactory {
    private val suffixes = listOf(
        "절약 팁", "줄이는 법", "아끼는 법", "대체 방법", "소비 줄이기",
        "돈 아끼기", "생활비 절약", "지출 관리", "현명한 소비", "비용 줄이기",
        "절약 습관", "가성비 대안", "알뜰 노하우", "지출 줄이는 방법", "예산 관리",
        "소비 습관 개선", "절약 챌린지", "대안 추천", "실천 방법", "절약 후기",
    )

    fun create(
        item: MissionItem,
        birthDate: LocalDate,
        address: ResidentialArea?,
        today: LocalDate,
        rotationSeed: Int,
    ): String {
        val ageGroup = ageGroup(Period.between(birthDate, today).years)
        val suffix = suffixes[Math.floorMod(rotationSeed, suffixes.size)]
        return listOfNotNull(ageGroup, address?.label, item.label, suffix).joinToString(" ")
    }

    private fun ageGroup(age: Int): String = when {
        age < 20 -> "10대"
        age >= 60 -> "60대 이상"
        else -> "${age / 10 * 10}대"
    }
}
