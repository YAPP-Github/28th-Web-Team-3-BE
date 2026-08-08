package backend.yapp.api.policy.controller

import backend.yapp.api.policy.dto.PolicyDetailResponse
import backend.yapp.api.policy.dto.PolicySummaryResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import backend.yapp.core.policy.service.PolicyQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
    @Operation(
        summary = "혜택 목록 조회",
        description = "청년정책(혜택) 목록을 페이지 단위로 조회한다. `category`로 4분류(금융/주거/복지/교육) 필터링. " +
            "온보딩에 생년월일이 입력돼 있으면 만 나이 대상에 해당하는 정책만 자동 노출한다(생년월일 미입력 시 연령 무관 전체). " +
            "각 항목의 `bookmarked`는 현재 게스트의 저장 여부.",
    )
    @GetMapping
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @Parameter(description = "혜택 필터 카테고리. 금융 / 주거 / 복지 / 교육 중 하나. 미지정 시 전체.")
        @RequestParam(required = false) category: String?,
        @Parameter(description = "페이지 번호(0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): List<PolicySummaryResponse> =
        policyQueryService.list(guestUserId, category, page, size).map { PolicySummaryResponse.from(it) }

    @Operation(summary = "혜택 상세 조회", description = "정책 상세 정보와 현재 게스트의 저장 여부(`bookmarked`)를 반환한다. 없으면 404.")
    @GetMapping("/{id}")
    fun detail(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long): PolicyDetailResponse =
        PolicyDetailResponse.from(policyQueryService.detail(guestUserId, id))

    @Operation(summary = "혜택 저장(북마크)", description = "정책을 저장 목록에 추가한다. 이미 저장돼 있으면 멱등 처리. 204 반환.")
    @PostMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun bookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.add(guestUserId, ContentType.POLICY, id)

    @Operation(summary = "혜택 저장 취소", description = "정책을 저장 목록에서 제거한다. 204 반환.")
    @DeleteMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unbookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.remove(guestUserId, ContentType.POLICY, id)
}
