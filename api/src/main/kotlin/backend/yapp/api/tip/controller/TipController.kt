package backend.yapp.api.tip.controller

import backend.yapp.api.tip.dto.TipDetailResponse
import backend.yapp.api.tip.dto.TipSummaryResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import backend.yapp.core.tip.service.TipQueryService
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

@Tag(name = "Tip", description = "블로그 팁 조회 및 저장(북마크). 콘텐츠 데이터는 후속 작업.")
@RestController
@RequestMapping("/api/tips")
class TipController(
    private val tipQueryService: TipQueryService,
    private val bookmarkService: BookmarkService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<TipSummaryResponse> =
        tipQueryService.list(guestUserId, category, page, size).map { TipSummaryResponse.from(it) }

    @GetMapping("/{id}")
    fun detail(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long): TipDetailResponse =
        TipDetailResponse.from(tipQueryService.detail(guestUserId, id))

    @PostMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun bookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.add(guestUserId, ContentType.TIP, id)

    @DeleteMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unbookmark(@AuthenticationPrincipal guestUserId: Long, @PathVariable id: Long) =
        bookmarkService.remove(guestUserId, ContentType.TIP, id)
}
