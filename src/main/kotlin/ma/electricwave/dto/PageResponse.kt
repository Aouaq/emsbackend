package ma.electricwave.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "Paginated response")
data class PageResponse<T : Any>(
    @field:Schema(description = "Page content")
    val content: List<T>,
    @field:Schema(description = "Zero-based page index", example = "0")
    val page: Int,
    @field:Schema(description = "Requested page size (max 100)", example = "20")
    val size: Int,
    @field:Schema(description = "Total matching elements", example = "42")
    val totalElements: Long,
    @field:Schema(description = "Total number of pages", example = "3")
    val totalPages: Int
) {
    companion object {
        const val MAX_PAGE_SIZE = 100

        fun <T : Any> from(page: Page<T>): PageResponse<T> = PageResponse(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }
}
