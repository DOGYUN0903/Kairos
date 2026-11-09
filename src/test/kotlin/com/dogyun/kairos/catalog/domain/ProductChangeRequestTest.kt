package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProductChangeRequestTest {

    private fun pending() = ProductChangeRequest.submit(1L, 1L, ProductContent("티셔츠", "설명", 5L, listOf(100L), emptyList()), 12000)

    private fun assertCode(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 제출하면_PENDING이다() {
        val request = pending()
        assertEquals(ChangeRequestStatus.PENDING, request.status)
        assertNull(request.rejectReason)
        assertEquals(12000, request.originalPrice)
    }

    @Test
    fun 승인된_요청은_철회할_수_없다() {
        val request = pending()
        request.approve()

        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { request.withdraw() }
        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { request.reject("사유") }
        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { request.approve() }
        assertEquals(ChangeRequestStatus.APPROVED, request.status)
    }

    @Test
    fun 반려하면_사유가_남고_철회한_요청은_승인할_수_없다() {
        val rejected = pending()
        rejected.reject("이미지가 흐립니다")
        assertEquals(ChangeRequestStatus.REJECTED, rejected.status)
        assertEquals("이미지가 흐립니다", rejected.rejectReason)

        val withdrawn = pending()
        withdrawn.withdraw()
        assertEquals(ChangeRequestStatus.WITHDRAWN, withdrawn.status)
        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { withdrawn.approve() }
    }
}
