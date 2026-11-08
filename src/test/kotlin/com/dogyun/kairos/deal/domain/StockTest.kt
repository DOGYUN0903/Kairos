package com.dogyun.kairos.deal.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockTest {

    @Test
    fun 차감하면_잔여가_줄어든다() {
        val stock = Stock(DealId(1), 10, 10)
        stock.decrease(3)
        assertEquals(7, stock.remaining)
    }

    @Test
    fun 재고보다_많이_차감하면_STOCK_SOLD_OUT() {
        val stock = Stock(DealId(1), 10, 2)
        val e = assertFailsWith<DomainException> { stock.decrease(3) }
        assertEquals(ErrorCode.STOCK_SOLD_OUT, e.errorCode)
        assertEquals(2, stock.remaining)
    }

    @Test
    fun 복구하면_잔여가_늘어난다() {
        val stock = Stock(DealId(1), 10, 5)
        stock.restore(3)
        assertEquals(8, stock.remaining)
    }

    @Test
    fun 복구는_총수량을_넘을_수_없다() {
        val stock = Stock(DealId(1), 10, 9)
        assertFailsWith<IllegalStateException> { stock.restore(2) }
        assertEquals(9, stock.remaining)
    }
}
