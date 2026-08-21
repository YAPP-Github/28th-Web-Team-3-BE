package backend.yapp.api.bookmark.controller

import backend.yapp.api.bookmark.dto.SavedContentResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Bookmark", description = "저장됨(북마크) 통합 목록. 유형(POLICY=혜택 / TIP=절약 팁)·카테고리 필터.")
@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController(
    private val bookmarkService: BookmarkService,
) {
    @Operation(
        summary = "저장됨(북마크) 목록 조회",
        description = "현재 게스트가 저장한 혜택·절약 팁을 최신순으로 통합 조회한다. `type`(POLICY/TIP)·`category`로 필터링 가능.",
    )
    @GetMapping
    fun saved(
        @AuthenticationPrincipal guestUserId: Long,
        @Parameter(description = "콘텐츠 유형 필터. POLICY(혜택) 또는 TIP(절약 팁). 미지정 시 전체.")
        @RequestParam(required = false) type: ContentType?,
        @Parameter(description = "카테고리 필터. 미지정 시 전체.")
        @RequestParam(required = false) category: String?,
    ): List<SavedContentResponse> =
        bookmarkService.saved(guestUserId, type, category).map { SavedContentResponse.from(it) }
}
