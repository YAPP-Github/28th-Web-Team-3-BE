package backend.yapp.api.bookmark.controller

import backend.yapp.api.bookmark.dto.SavedContentResponse
import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.BookmarkService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Bookmark", description = "저장됨(북마크) 통합 목록. 유형(POLICY/TIP)·카테고리 필터.")
@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController(
    private val bookmarkService: BookmarkService,
) {
    @GetMapping
    fun saved(
        @AuthenticationPrincipal guestUserId: Long,
        @RequestParam(required = false) type: ContentType?,
        @RequestParam(required = false) category: String?,
    ): List<SavedContentResponse> =
        bookmarkService.saved(guestUserId, type, category).map { SavedContentResponse.from(it) }
}
