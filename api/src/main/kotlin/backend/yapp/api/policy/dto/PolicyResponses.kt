package backend.yapp.api.policy.dto

import backend.yapp.core.policy.service.PolicyDetail
import backend.yapp.core.policy.service.PolicySummary
import backend.yapp.core.policy.service.PolicySyncResult

data class PolicySummaryResponse(
    val id: Long,
    val title: String,
    val category: String?,
    val largeCategory: String?,
    val description: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(summary: PolicySummary) =
            PolicySummaryResponse(
                summary.id,
                summary.title,
                summary.category,
                summary.largeCategory,
                summary.description,
                summary.bookmarked,
            )
    }
}

data class PolicyDetailResponse(
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
    val bookmarked: Boolean,
) {
    companion object {
        fun from(detail: PolicyDetail) =
            PolicyDetailResponse(
                id = detail.id,
                title = detail.title,
                description = detail.description,
                supportContent = detail.supportContent,
                category = detail.category,
                largeCategory = detail.largeCategory,
                mediumCategory = detail.mediumCategory,
                supervisingOrg = detail.supervisingOrg,
                applyUrl = detail.applyUrl,
                applyPeriodText = detail.applyPeriodText,
                applyMethod = detail.applyMethod,
                submitDocuments = detail.submitDocuments,
                targetMinAge = detail.targetMinAge,
                targetMaxAge = detail.targetMaxAge,
                earnCondition = detail.earnCondition,
                additionalQualification = detail.additionalQualification,
                bookmarked = detail.bookmarked,
            )
    }
}

data class PolicySyncResponse(
    val fetched: Int,
    val upserted: Int,
    val skipped: Int,
) {
    companion object {
        fun from(result: PolicySyncResult) = PolicySyncResponse(result.fetched, result.upserted, result.skipped)
    }
}
