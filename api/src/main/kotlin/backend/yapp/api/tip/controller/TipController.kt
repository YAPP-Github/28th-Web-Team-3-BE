package backend.yapp.api.tip.controller

import backend.yapp.api.tip.dto.TipDetailResponse
import backend.yapp.api.tip.dto.TipSummaryResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import backend.yapp.core.tip.service.TipQueryService
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

@Tag(name = "절약 팁", description = "절약 팁 조회 및 저장(북마크). 카테고리(식비/생활/취미)·선택항목으로 필터.")
@RestController
@RequestMapping("/api/tips")
class TipController(
    private val tipQueryService: TipQueryService,
    private val bookmarkService: BookmarkService,
) {
    @Operation(
        summary = "절약 팁 목록 조회",
        description = "절약 팁 목록을 페이지 단위로 조회한다. `category`(식비/생활/취미)·`subcategory`(선택항목)로 필터링 가능. " +
            "각 항목의 `bookmarked`는 현재 게스트의 저장 여부.",
    )
    @GetMapping
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @Parameter(description = "카테고리 필터. 식비 / 생활 / 취미 중 하나. 미지정 시 전체.")
        @RequestParam(required = false) category: String?,
        @Parameter(description = "선택항목(세부 분류) 필터. 예: 배달음식, 편의점, 화장품. 미지정 시 전체.")
        @RequestParam(required = false) subcategory: String?,
        @Parameter(description = "페이지 번호(0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): List<TipSummaryResponse> =
        tipQueryService.list(guestUserId, category, subcategory, page, size).map { TipSummaryResponse.from(it) }

    @Operation(summary = "절약 팁 상세 조회", description = "절약 팁 상세와 현재 게스트의 저장 여부(`bookmarked`)를 반환한다. 없으면 404.")
    @GetMapping("/{id}")
    fun detail(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long): TipDetailResponse =
        TipDetailResponse.from(tipQueryService.detail(guestUserId, id))

    @Operation(summary = "절약 팁 저장(북마크)", description = "절약 팁을 저장 목록에 추가한다. 이미 저장돼 있으면 멱등 처리. 204 반환.")
    @PostMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun bookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.add(guestUserId, ContentType.TIP, id)

    @Operation(summary = "절약 팁 저장 취소", description = "절약 팁을 저장 목록에서 제거한다. 204 반환.")
    @DeleteMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unbookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.remove(guestUserId, ContentType.TIP, id)
}
