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
    fun 복구가_총수량을_넘어도_Phase1에서는_허용된다() {
        val stock = Stock(DealId(1), 10, 9)
        stock.restore(2)
        assertEquals(11, stock.remaining)
    }

    @Test
    fun 잔여와_같은_수량을_차감하면_0이_된다() {
        val stock = Stock(DealId(1), 10, 4)
        stock.decrease(4)
        assertEquals(0, stock.remaining)
    }

    @Test
    fun 총수량까지_정확히_복구할_수_있다() {
        val stock = Stock(DealId(1), 10, 6)
        stock.restore(4)
        assertEquals(10, stock.remaining)
    }
}
