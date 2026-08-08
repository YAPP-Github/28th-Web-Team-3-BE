package backend.yapp.core.policy.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.bookmark.domain.ContentBookmarkRepository
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.policy.domain.YouthPolicy
import backend.yapp.core.policy.domain.YouthPolicyRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 혜택(청년정책) 목록 요약. 요약값 없이 정책명·대분류·설명만 노출한다. */
data class PolicySummary(
    val id: Long,
    val title: String,
    val largeCategory: String?,
    val description: String?,
    val bookmarked: Boolean,
)

/** 혜택 상세. */
data class PolicyDetail(
    val id: Long,
    val title: String,
    val description: String?,
    val supportContent: String?,
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
    val bookmarked: Boolean,
)

@Service
class PolicyQueryService(
    private val policyRepository: YouthPolicyRepository,
    private val bookmarkRepository: ContentBookmarkRepository,
) {
    @Transactional(readOnly = true)
    fun list(guestUserId: Long, category: String?, page: Int, size: Int): List<PolicySummary> {
        val pageable = PageRequest.of(page, size)
        val policies = if (category.isNullOrBlank()) {
            policyRepository.findAll(pageable).content
        } else {
            policyRepository.findByLargeCategoryContaining(category, pageable).content
        }
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
        PolicySummary(id, title, largeCategory, description, bookmarked)

    private fun YouthPolicy.toDetail(bookmarked: Boolean) =
        PolicyDetail(
            id = id,
            title = title,
            description = description,
            supportContent = supportContent,
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
            bookmarked = bookmarked,
        )
}
