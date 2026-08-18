package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import java.time.Period

object MissionSearchQueryFactory {
    fun create(
        item: MissionItem,
        birthDate: LocalDate,
        address: ResidentialArea?,
        today: LocalDate,
        baselineFrequency: Int,
        baselineAmountWon: Int,
    ): String {
        val ageGroup = ageGroup(Period.between(birthDate, today).years)
        return listOf(
            "항목=${item.label}",
            "사용자=${listOfNotNull(ageGroup, address?.label).joinToString(" ")}",
            "소비=${baselineFrequency}회 ${baselineAmountWon}원",
        ).joinToString(" | ")
    }

    private fun ageGroup(age: Int): String = when {
        age < 20 -> "10대"
        age >= 60 -> "60대 이상"
        else -> "${age / 10 * 10}대"
    }
}
