package backend.yapp.core.mission.generation.service

import backend.yapp.common.exception.BaseException
import backend.yapp.core.mission.generation.domain.MissionCategory
import backend.yapp.core.mission.generation.domain.MissionDraft
import backend.yapp.core.mission.generation.domain.MissionDraftRepository
import backend.yapp.core.mission.generation.domain.MissionDraftTemplate
import backend.yapp.core.mission.generation.domain.MissionDraftTemplateRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJob
import backend.yapp.core.mission.generation.domain.MissionGenerationJobRepository
import backend.yapp.core.mission.generation.domain.MissionGenerationJobStatus
import backend.yapp.core.mission.generation.domain.MissionItem
import backend.yapp.core.mission.generation.domain.MissionMetricType
import backend.yapp.core.mission.generation.domain.MissionRepository
import backend.yapp.core.mission.generation.domain.MissionTargetFormula
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationPort
import backend.yapp.core.mission.generation.port.MissionAlternativeGenerationResult
import backend.yapp.core.mission.generation.port.MissionAlternativeTemplate
import backend.yapp.core.mission.generation.port.MissionDraftGenerationSource
import backend.yapp.core.onboarding.domain.OnboardingProfile
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.domain.OnboardingStatus
import backend.yapp.core.onboarding.domain.ResidentialArea
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MissionGenerationServiceTest {
    private val now = Instant.parse("2026-07-23T00:00:00Z")

    @Test
    fun `request reuses an active generation job`() {
        val fixture = fixture()
        val existing = MissionGenerationJob(
            id = UUID.randomUUID(),
            guestUserId = GUEST_USER_ID,
            category = MissionCategory.MEAL,
            item = MissionItem.DELIVERY_FOOD,
            baselineFrequency = 1,
            baselineAmountWon = 1,
            createdAt = now,
        ).also { it.start(now) }
        readyOnboarding(fixture)
        `when`(
            fixture.jobRepository.findFirstByGuestUserIdAndActiveGenerationKeyOrderByCreatedAtDesc(
                GUEST_USER_ID,
                MissionGenerationJob.ACTIVE_KEY,
            ),
        ).thenReturn(existing)

        val result = fixture.service.request(
            GUEST_USER_ID,
            MissionCategory.MEAL,
            MissionItem.DELIVERY_FOOD,
            1,
            1,
        )

        assertEquals(existing.id, result.jobId)
        assertEquals(MissionGenerationJobStatus.RUNNING, result.status)
        verify(fixture.jobRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(MissionGenerationJob::class.java))
        verify(fixture.draftRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `request completes a job and persists three full-baseline drafts without an outbox`() {
        val fixture = fixture()
        readyOnboarding(fixture)
        `when`(fixture.jobRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MissionGenerationJob::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] }
        `when`(fixture.templateRepository.findByTargetCodeAndActiveTrue(MissionItem.DELIVERY_FOOD.name))
            .thenReturn(template())
        fixture.alternativeGenerator.result = MissionAlternativeGenerationResult(
            alternatives = listOf(
                MissionAlternativeTemplate("배달 대신 집밥 {count}회", "설명 1"),
                MissionAlternativeTemplate("배달 전 예산 확인 {count}회", "설명 2"),
                MissionAlternativeTemplate("보유 식재료 활용 {count}회", "설명 3"),
            ),
            source = MissionDraftGenerationSource.DIRECT,
        )

        val result = fixture.service.request(
            GUEST_USER_ID,
            MissionCategory.MEAL,
            MissionItem.DELIVERY_FOOD,
            5,
            50_000,
        )

        assertEquals(MissionGenerationJobStatus.SUCCEEDED, result.status)
        assertEquals(MissionDraftGenerationSource.DIRECT, result.generationSource)
        assertEquals(true, result.draftsAvailable)
        @Suppress("UNCHECKED_CAST")
        val drafts = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MissionDraft>>
        verify(fixture.draftRepository).saveAll(drafts.capture())
        assertEquals(3, drafts.value.size)
        assertEquals(listOf(5, 5, 5), drafts.value.map(MissionDraft::targetCount))
        assertEquals(listOf(50_000, 50_000, 50_000), drafts.value.map(MissionDraft::estimatedSavingsWon))
        assertEquals("V2_DIRECT_CANDIDATE", drafts.value.first().savingsEstimateVersion)
        assertEquals("배달 대신 집밥 5회", drafts.value.first().title)
    }

    @Test
    fun `confirm rejects more than three selected drafts`() {
        val fixture = fixture()

        assertFailsWith<BaseException> {
            fixture.service.confirm(GUEST_USER_ID, UUID.randomUUID(), List(4) { UUID.randomUUID() })
        }
    }

    private fun readyOnboarding(fixture: Fixture) {
        `when`(fixture.onboardingRepository.findByGuestUserIdForUpdate(GUEST_USER_ID))
            .thenReturn(
                OnboardingProfile(
                    GUEST_USER_ID,
                    birthDate = LocalDate.of(2000, 1, 1),
                    address = ResidentialArea.SEOUL,
                    status = OnboardingStatus.COMPLETED,
                ),
            )
    }

    private fun template() = MissionDraftTemplate(
        category = MissionCategory.MEAL,
        title = "기본 제목",
        description = "기본 설명",
        actionCode = MissionItem.DELIVERY_FOOD.name,
        metricType = MissionMetricType.COUNT,
        targetCount = 1,
        targetUnit = "TIMES_PER_WEEK",
        estimatedSavingsWon = 1,
        targetCode = MissionItem.DELIVERY_FOOD.name,
        targetFormula = MissionTargetFormula.FIXED,
        sortOrder = 1,
        id = 1,
    )

    private fun fixture(): Fixture {
        val jobRepository = mock(MissionGenerationJobRepository::class.java)
        val onboardingRepository = mock(OnboardingProfileRepository::class.java)
        val draftRepository = mock(MissionDraftRepository::class.java)
        val missionRepository = mock(MissionRepository::class.java)
        val templateRepository = mock(MissionDraftTemplateRepository::class.java)
        val alternativeGenerator = RecordingAlternativeGenerator()
        val service = MissionGenerationService(
            jobRepository = jobRepository,
            draftRepository = draftRepository,
            missionRepository = missionRepository,
            templateRepository = templateRepository,
            alternativeGenerator = alternativeGenerator,
            onboardingProfileRepository = onboardingRepository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Fixture(service, jobRepository, onboardingRepository, draftRepository, templateRepository, alternativeGenerator)
    }

    private data class Fixture(
        val service: MissionGenerationService,
        val jobRepository: MissionGenerationJobRepository,
        val onboardingRepository: OnboardingProfileRepository,
        val draftRepository: MissionDraftRepository,
        val templateRepository: MissionDraftTemplateRepository,
        val alternativeGenerator: RecordingAlternativeGenerator,
    )

    private class RecordingAlternativeGenerator : MissionAlternativeGenerationPort {
        var result = MissionAlternativeGenerationResult(emptyList(), MissionDraftGenerationSource.DIRECT)

        override fun generate(request: backend.yapp.core.mission.generation.port.MissionAlternativeGenerationRequest) = result
    }

    companion object {
        private const val GUEST_USER_ID = 1L
    }
}
