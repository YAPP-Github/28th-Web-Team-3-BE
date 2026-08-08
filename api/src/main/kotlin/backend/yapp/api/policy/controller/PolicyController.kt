package backend.yapp.api.policy.controller

import backend.yapp.api.policy.dto.PolicyDetailResponse
import backend.yapp.api.policy.dto.PolicySummaryResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import backend.yapp.core.policy.service.PolicyQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Policy", description = "혜택(청년정책) 조회 및 저장(북마크)")
@RestController
@RequestMapping("/api/policies")
class PolicyController(
    private val policyQueryService: PolicyQueryService,
    private val bookmarkService: BookmarkService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<PolicySummaryResponse> =
        policyQueryService.list(guestUserId, category, page, size).map { PolicySummaryResponse.from(it) }

    @GetMapping("/{id}")
    fun detail(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long): PolicyDetailResponse =
        PolicyDetailResponse.from(policyQueryService.detail(guestUserId, id))

    @PostMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun bookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.add(guestUserId, ContentType.POLICY, id)

    @DeleteMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unbookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.remove(guestUserId, ContentType.POLICY, id)
}
