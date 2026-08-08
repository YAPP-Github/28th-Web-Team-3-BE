package backend.yapp.core.policy.service

import backend.yapp.core.policy.domain.YouthPolicy
import backend.yapp.core.policy.domain.YouthPolicyRepository
import backend.yapp.core.policy.port.ExternalYouthPolicy
import backend.yapp.core.policy.port.YouthPolicyProviderPort
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 청년정책 동기화 결과 요약. */
data class PolicySyncResult(
    val fetched: Int,
    val upserted: Int,
    val skipped: Int,
)

/**
 * 온통청년 API를 페이지 순회하며 스코프 필터를 통과한 정책만 DB에 upsert 한다.
 * Cloud Scheduler가 하루 1회 트리거하는 동기화 endpoint에서 호출된다.
 */
@Service
class PolicySyncService(
    private val provider: YouthPolicyProviderPort,
    private val repository: YouthPolicyRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun sync(): PolicySyncResult {
        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        var pageNum = 1
        var fetched = 0
        var upserted = 0

        while (pageNum <= MAX_PAGES) {
            val page = provider.fetch(pageNum, PAGE_SIZE)
            if (page.policies.isEmpty()) break
            fetched += page.policies.size
            upserted += upsertInScope(page.policies, today)

            if (fetched >= page.totalCount) break
            pageNum++
        }
        return PolicySyncResult(fetched = fetched, upserted = upserted, skipped = fetched - upserted)
    }

    /**
     * 온통청년 API 응답에서 파싱한 정책 목록을 스코프 필터 후 upsert 한다.
     * 클라우드 IP 차단으로 서버가 직접 호출하지 못할 때, 사람이 받아온 데이터를 업로드해 갱신하는 경로에서 사용한다.
     */
    @Transactional
    fun ingest(policies: List<ExternalYouthPolicy>): PolicySyncResult {
        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        val upserted = upsertInScope(policies, today)
        return PolicySyncResult(fetched = policies.size, upserted = upserted, skipped = policies.size - upserted)
    }

    private fun upsertInScope(policies: List<ExternalYouthPolicy>, today: LocalDate): Int {
        var upserted = 0
        for (external in policies) {
            if (!PolicyScopeFilter.isInScope(external, today)) continue
            upsert(external)
            upserted++
        }
        return upserted
    }

    private fun upsert(external: ExternalYouthPolicy) {
        val now = clock.instant()
        val deadline = PolicyScopeFilter.resolveEndDate(external)
        val existing = repository.findByExternalId(external.externalId)
        if (existing != null) {
            existing.apply(external, deadline, now)
            repository.save(existing)
        } else {
            repository.save(newPolicy(external, deadline, now))
        }
    }

    private fun YouthPolicy.apply(external: ExternalYouthPolicy, deadline: LocalDate?, now: java.time.Instant) {
        title = external.title
        description = external.description
        supportContent = external.supportContent
        largeCategory = external.largeCategory
        mediumCategory = external.mediumCategory
        supervisingOrg = external.supervisingOrg
        applyUrl = external.applyUrl
        applyPeriodText = external.applyPeriodText
        applyDeadline = deadline
        applyMethod = external.applyMethod
        submitDocuments = external.submitDocuments
        targetMinAge = external.targetMinAge
        targetMaxAge = external.targetMaxAge
        earnCondition = external.earnCondition
        additionalQualification = external.additionalQualification
        externalModifiedAt = external.externalModifiedAt
        updatedAt = now
    }

    private fun newPolicy(external: ExternalYouthPolicy, deadline: LocalDate?, now: java.time.Instant): YouthPolicy =
        YouthPolicy(
            externalId = external.externalId,
            title = external.title,
            description = external.description,
            supportContent = external.supportContent,
            largeCategory = external.largeCategory,
            mediumCategory = external.mediumCategory,
            supervisingOrg = external.supervisingOrg,
            applyUrl = external.applyUrl,
            applyPeriodText = external.applyPeriodText,
            applyDeadline = deadline,
            applyMethod = external.applyMethod,
            submitDocuments = external.submitDocuments,
            targetMinAge = external.targetMinAge,
            targetMaxAge = external.targetMaxAge,
            earnCondition = external.earnCondition,
            additionalQualification = external.additionalQualification,
            externalModifiedAt = external.externalModifiedAt,
            createdAt = now,
            updatedAt = now,
        )

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 500
    }
}
