package com.dogyun.kairos.common.domain

data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> of(content: List<T>, page: Int, size: Int, totalElements: Long): PageResult<T> {
            val totalPages = if (size == 0) 0 else ((totalElements + size - 1) / size).toInt()
            return PageResult(content, page, size, totalElements, totalPages, page + 1 < totalPages)
        }
    }
}
