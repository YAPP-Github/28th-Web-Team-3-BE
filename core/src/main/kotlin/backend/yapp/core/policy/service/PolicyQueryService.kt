package backend.yapp.core.policy.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.bookmark.domain.ContentBookmarkRepository
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.policy.domain.YouthPolicy
import backend.yapp.core.policy.domain.YouthPolicyRepository
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 혜택(청년정책) 목록 요약. 요약값 없이 정책명·카테고리·설명·조회수만 노출한다. */
data class PolicySummary(
    val id: Long,
    val title: String,
    val category: String?,
    val largeCategory: String?,
    val description: String?,
    val viewCount: Int,
    val bookmarked: Boolean,
)

/** 혜택 상세. */
data class PolicyDetail(
    val id: Long,
    val title: String,
    val description: String?,
    val supportContent: String?,
    val category: String?,
    val largeCategory: String?,
    val mediumCategory: String?,
    val supervisingOrg: String?,
    val applyUrl: String?,
    val applyPeriodText: String?,
    val applyMethod: String?,
    val submitDocuments: String?,
    val targetMinAge: Int?,
    val targetMaxAge: Int?,
    val earnCondition: String?,
    val additionalQualification: String?,
    val viewCount: Int,
    val bookmarked: Boolean,
)

@Service
class PolicyQueryService(
    private val policyRepository: YouthPolicyRepository,
    private val bookmarkRepository: ContentBookmarkRepository,
    private val profileRepository: OnboardingProfileRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * 혜택 목록. 게스트의 온보딩 정보로 자신에게 해당되는 정책만 노출한다.
     * - 생년월일이 있으면 만 나이 대상 정책만, 없으면 연령 무관.
     * - 거주지역이 있으면 해당 지역(+전국) 정책만, 없으면 지역 무관.
     */
    @Transactional(readOnly = true)
    fun list(guestUserId: Long, category: String?, page: Int, size: Int): List<PolicySummary> {
        val pageable = PageRequest.of(page, size)
        val profile = profileRepository.findByGuestUserId(guestUserId)
        val age = profile?.birthDate?.let { Period.between(it, LocalDate.ofInstant(clock.instant(), ZONE)).years }
        val regionToken = profile?.address?.let { "%${PolicyScopeFilter.REGION_DELIMITER}${it.name}${PolicyScopeFilter.REGION_DELIMITER}%" }
        val policies = policyRepository
            .search(category?.takeIf { it.isNotBlank() }, age, regionToken, pageable)
            .content
        val bookmarkedIds = bookmarkedIds(guestUserId, policies.map { it.id })
        return policies.map { it.toSummary(it.id in bookmarkedIds) }
    }

    @Transactional(readOnly = true)
    fun detail(guestUserId: Long, id: Long): PolicyDetail {
        val policy = policyRepository.findById(id).orElseThrow { BaseException(ErrorCode.POLICY_NOT_FOUND) }
        val bookmarked = bookmarkRepository
            .existsByGuestUserIdAndContentTypeAndContentId(guestUserId, ContentType.POLICY, id)
        return policy.toDetail(bookmarked)
    }

    private fun bookmarkedIds(guestUserId: Long, ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        return bookmarkRepository
            .findByGuestUserIdAndContentTypeAndContentIdIn(guestUserId, ContentType.POLICY, ids)
            .map { it.contentId }
            .toSet()
    }

    private fun YouthPolicy.toSummary(bookmarked: Boolean) =
        PolicySummary(id, title, category, largeCategory, description, viewCount, bookmarked)

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun YouthPolicy.toDetail(bookmarked: Boolean) =
        PolicyDetail(
            id = id,
            title = title,
            description = description,
            supportContent = supportContent,
            category = category,
            largeCategory = largeCategory,
            mediumCategory = mediumCategory,
            supervisingOrg = supervisingOrg,
            applyUrl = applyUrl,
            applyPeriodText = applyPeriodText,
            applyMethod = applyMethod,
            submitDocuments = submitDocuments,
            targetMinAge = targetMinAge,
            targetMaxAge = targetMaxAge,
            earnCondition = earnCondition,
            additionalQualification = additionalQualification,
            viewCount = viewCount,
            bookmarked = bookmarked,
        )
}
