package com.dogyun.kairos.common.adapter.`in`.web

import com.dogyun.kairos.common.domain.PageResult

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> from(result: PageResult<T>): PageResponse<T> = PageResponse(
            content = result.content,
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}
