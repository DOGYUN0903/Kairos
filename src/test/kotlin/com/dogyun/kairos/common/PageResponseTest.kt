package com.dogyun.kairos.common

import com.dogyun.kairos.common.adapter.`in`.web.PageResponse
import com.dogyun.kairos.common.domain.PageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageResponseTest {

    @Test
    fun `PageResult를_PageResponse로_변환한다`() {
        val middle = PageResponse.from(PageResult(listOf("a", "b"), page = 0, size = 2, totalElements = 5, totalPages = 3, hasNext = true))
        assertEquals(listOf("a", "b"), middle.content)
        assertEquals(0, middle.page)
        assertEquals(2, middle.size)
        assertEquals(5L, middle.totalElements)
        assertEquals(3, middle.totalPages)
        assertTrue(middle.hasNext)

        val last = PageResponse.from(PageResult(listOf("e"), page = 2, size = 2, totalElements = 5, totalPages = 3, hasNext = false))
        assertFalse(last.hasNext)
    }

    @Test
    fun `PageResult는_전체_건수로_hasNext를_계산한다`() {
        assertTrue(PageResult.of(listOf(1, 2), page = 0, size = 2, totalElements = 5).hasNext)
        assertFalse(PageResult.of(listOf(5), page = 2, size = 2, totalElements = 5).hasNext)
        assertEquals(3, PageResult.of(listOf(5), page = 2, size = 2, totalElements = 5).totalPages)
    }
}
