package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MissionCandidateServiceTest {
    private val onboardingProfileRepository = mock(OnboardingProfileRepository::class.java)
    @Test
    fun `allocates requested frequency and savings across alternatives`() {
        completedOnboarding()
        val service = MissionCandidateService(
            onboardingProfileRepository,
            object : MissionAlternativeGenerationPort {
                override fun generate(request: backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest) =
                    MissionAlternativeGenerationResult(
                    alternatives = listOf(
                        MissionAlternativeTemplate("집밥으로 {count}회 대체하기", "설명 1"),
                        MissionAlternativeTemplate("쿠폰으로 {count}번 절약하기", "설명 2"),
                        MissionAlternativeTemplate("할인 메뉴를 {count}회 이용하기", "설명 3"),
                    ),
                    source = MissionDraftGenerationSource.DIRECT,
                    )
            },
        )

        val candidates = service.candidates(
            guestUserId = GUEST_USER_ID,
            category = MissionCategory.MEAL,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 7,
            baselineAmountWon = 70_000,
        )

        assertEquals(3, candidates.size)
        assertEquals(listOf("집밥으로 3회 대체하기", "쿠폰으로 2번 절약하기", "할인 메뉴를 2회 이용하기"), candidates.map { it.title })
        assertEquals(listOf(3, 2, 2), candidates.map { it.targetCount })
        assertEquals(listOf(30_000, 20_000, 20_000), candidates.map { it.estimatedSavingsWon })
        assertEquals(listOf("V2_DETERMINISTIC", "V2_DETERMINISTIC", "V2_DETERMINISTIC"), candidates.map { it.savingsEstimateVersion })
    }

    @Test
    fun `limits candidates to the baseline frequency below three`() {
        completedOnboarding()
        val service = MissionCandidateService(
            onboardingProfileRepository,
            fixedAlternativeGenerator(),
        )

        val oneTimeCandidates = service.candidates(
            guestUserId = GUEST_USER_ID,
            category = MissionCategory.MEAL,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 10_000,
        )
        val twoTimeCandidates = service.candidates(
            guestUserId = GUEST_USER_ID,
            category = MissionCategory.MEAL,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 2,
            baselineAmountWon = 20_000,
        )

        assertEquals(listOf(1), oneTimeCandidates.map { it.targetCount })
        assertEquals(listOf(1, 1), twoTimeCandidates.map { it.targetCount })
    }

    @Test
    fun `rejects candidate generation before onboarding is completed`() {
        `when`(onboardingProfileRepository.findByGuestUserId(GUEST_USER_ID)).thenReturn(null)
        val service = MissionCandidateService(
            onboardingProfileRepository,
            object : MissionAlternativeGenerationPort {
                override fun generate(request: backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest): MissionAlternativeGenerationResult =
                    error("generator must not be called")
            },
        )

        assertFailsWith<BaseException> {
            service.candidates(
                guestUserId = GUEST_USER_ID,
                category = MissionCategory.MEAL,
                item = MissionItem.DELIVERY_FOOD,
                baselineFrequency = 1,
                baselineAmountWon = 15_000,
            )
        }
    }

    private fun completedOnboarding() {
        `when`(onboardingProfileRepository.findByGuestUserId(GUEST_USER_ID)).thenReturn(
            OnboardingProfile(
                guestUserId = GUEST_USER_ID,
                birthDate = LocalDate.of(2000, 1, 1),
                address = ResidentialArea.SEOUL,
                status = OnboardingStatus.COMPLETED,
            ),
        )
    }

    private fun fixedAlternativeGenerator(): MissionAlternativeGenerationPort =
        object : MissionAlternativeGenerationPort {
            override fun generate(
                request: backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest,
            ): MissionAlternativeGenerationResult =
                MissionAlternativeGenerationResult(
                alternatives = listOf(
                    MissionAlternativeTemplate("집밥으로 {count}회 대체하기", "설명 1"),
                    MissionAlternativeTemplate("쿠폰으로 {count}번 절약하기", "설명 2"),
                    MissionAlternativeTemplate("할인 메뉴를 {count}회 이용하기", "설명 3"),
                ),
                source = MissionDraftGenerationSource.DIRECT,
            )
        }

    companion object {
        private const val GUEST_USER_ID = 1L
    }
}
