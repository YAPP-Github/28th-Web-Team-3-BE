package backend.yapp.api.bookmark.dto

import backend.yapp.core.bookmark.domain.ContentType
import backend.yapp.core.bookmark.service.SavedContent

data class SavedContentResponse(
    val contentType: ContentType,
    val id: Long,
    val title: String,
    val category: String?,
    val description: String?,
) {
    companion object {
        fun from(saved: SavedContent) =
            SavedContentResponse(saved.contentType, saved.id, saved.title, saved.category, saved.description)
    }
}
