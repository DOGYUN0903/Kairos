package com.dogyun.kairos.deal.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DealTest {

    private val openAt = LocalDateTime.of(2026, 11, 10, 10, 0)
    private val closeAt = LocalDateTime.of(2026, 11, 10, 12, 0)

    private fun deal(
        original: Long = 10_000,
        price: Long = 7_000,
        quantity: Int = 100,
        limit: Int = 2,
        open: LocalDateTime = openAt,
        close: LocalDateTime = closeAt,
    ) = Deal(null, "상품", Money(original), Money(price), quantity, limit, open, close)

    private fun assertInvalid(block: () -> Unit) {
        assertEquals(ErrorCode.DEAL_INVALID, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 할인가가_정가보다_크면_DEAL_INVALID() {
        assertInvalid { deal(original = 10_000, price = 10_001) }
    }

    @Test
    fun 수량이나_1인제한이_1미만이면_DEAL_INVALID() {
        assertInvalid { deal(quantity = 0) }
        assertInvalid { deal(limit = 0) }
    }

    @Test
    fun 종료가_오픈보다_빠르면_DEAL_INVALID() {
        assertInvalid { deal(open = closeAt, close = openAt) }
        assertInvalid { deal(open = openAt, close = openAt) }
    }

    @Test
    fun 시각에_따라_상태가_계산된다() {
        val deal = deal()
        assertEquals(DealStatus.SCHEDULED, deal.statusAt(openAt.minusSeconds(1)))
        assertEquals(DealStatus.ONGOING, deal.statusAt(openAt))
        assertEquals(DealStatus.ONGOING, deal.statusAt(closeAt.minusSeconds(1)))
        assertEquals(DealStatus.CLOSED, deal.statusAt(closeAt))
    }

    @Test
    fun 오픈_전에는_수정할_수_있다() {
        val deal = deal()
        deal.update("새상품", Money(20_000), Money(15_000), 50, 1, openAt, closeAt, openAt.minusHours(1))
        assertEquals("새상품", deal.productName)
        assertEquals(50, deal.totalQuantity)
    }

    @Test
    fun 오픈_후_수정은_DEAL_ALREADY_OPENED() {
        val e = assertFailsWith<DomainException> {
            deal().update("새상품", Money(20_000), Money(15_000), 50, 1, openAt, closeAt, openAt)
        }
        assertEquals(ErrorCode.DEAL_ALREADY_OPENED, e.errorCode)
    }

    @Test
    fun 오픈_후_삭제는_DEAL_ALREADY_OPENED() {
        val e = assertFailsWith<DomainException> { deal().ensureDeletable(openAt) }
        assertEquals(ErrorCode.DEAL_ALREADY_OPENED, e.errorCode)
    }

    @Test
    fun 오픈_전에는_삭제할_수_있다() {
        deal().ensureDeletable(openAt.minusSeconds(1))
    }
}
