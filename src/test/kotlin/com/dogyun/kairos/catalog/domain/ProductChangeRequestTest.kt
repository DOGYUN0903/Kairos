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

    private fun product(content: ProductContent, originalPrice: Long = 12000) = Product.restore(
        ProductId(1L), 1L, 1L, content, originalPrice, 9000, ProductStatus.ON_SALE, null, null, 0, emptyList(),
    )

    @Test
    fun 상품과_같은_내용이면_changedFields는_비어_있다() {
        val content = ProductContent("티셔츠", "설명", 5L, listOf(100L), listOf(ProductOption(1L, "색상", listOf(OptionValue(11L, "블랙")))))
        val request = ProductChangeRequest.submit(1L, 1L, content, 12000)

        assertEquals(emptyList(), request.changedFields(product(content)))
    }

    @Test
    fun changedFields는_상품과_다른_항목만_고정_순서로_돌려준다() {
        val current = ProductContent("티셔츠", "설명", 5L, listOf(100L, 101L), listOf(ProductOption(1L, "색상", listOf(OptionValue(11L, "블랙")))))
        val requested = ProductContent(
            "새 티셔츠", "설명", 6L, listOf(101L, 100L, 102L),
            listOf(ProductOption(1L, "색상", listOf(OptionValue(11L, "블랙"), OptionValue(null, "네이비")))),
        )
        val request = ProductChangeRequest.submit(1L, 1L, requested, 15000)

        assertEquals(
            listOf(ChangedField.NAME, ChangedField.CATEGORY, ChangedField.IMAGES, ChangedField.OPTIONS, ChangedField.ORIGINAL_PRICE),
            request.changedFields(product(current)),
        )
    }

    @Test
    fun 설명만_바뀌면_DESCRIPTION만_돌려준다() {
        val current = ProductContent("티셔츠", "설명", 5L, listOf(100L), emptyList())
        val request = ProductChangeRequest.submit(1L, 1L, current.copy(description = "새 설명"), 12000)

        assertEquals(listOf(ChangedField.DESCRIPTION), request.changedFields(product(current)))
    }
}
