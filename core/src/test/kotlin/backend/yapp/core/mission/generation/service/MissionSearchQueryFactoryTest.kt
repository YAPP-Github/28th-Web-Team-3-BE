package backend.yapp.core.mission.generation.service

import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionSearchQueryFactoryTest {
    @Test
    fun `semantic query uses only item demographic and consumption inputs`() {
        val query = MissionSearchQueryFactory.create(
            item = MissionItem.CONVENIENCE_STORE,
            birthDate = LocalDate.of(1998, 3, 1),
            address = ResidentialArea.SEOUL,
            today = LocalDate.of(2026, 8, 18),
            baselineFrequency = 4,
            baselineAmountWon = 35_000,
        )

        assertEquals("항목=편의점 | 사용자=20대 서울 | 소비=4회 35000원", query)
    }
}
