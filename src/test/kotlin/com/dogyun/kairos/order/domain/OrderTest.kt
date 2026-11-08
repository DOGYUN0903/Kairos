package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderTest {

    private fun created() = Order.create(memberId = 1L, dealId = 10L, quantity = 2, unitPrice = Money(5000))

    @Test
    fun 생성하면_CREATED이고_금액은_단가x수량이다() {
        val order = created()
        assertEquals(OrderStatus.CREATED, order.status)
        assertEquals(Money(10000), order.amount)
        assertNull(order.cancelReason)
        assertNull(order.id)
    }

    @Test
    fun 결제완료로_전이() {
        val order = created()
        order.markPaid()
        assertEquals(OrderStatus.PAID, order.status)
        assertNull(order.cancelReason)
    }

    @Test
    fun 취소하면_사유가_남는다() {
        val order = created()
        order.cancel(CancelReason.PAYMENT_FAILED)
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(CancelReason.PAYMENT_FAILED, order.cancelReason)
    }

    @Test
    fun 취소된_주문은_다시_취소할_수_없다() {
        val order = created()
        order.cancel(CancelReason.PAYMENT_TIMEOUT)
        val e = assertFailsWith<DomainException> { order.cancel(CancelReason.USER_REQUESTED) }
        assertEquals(ErrorCode.ORDER_ALREADY_CANCELLED, e.errorCode)
        assertEquals(CancelReason.PAYMENT_TIMEOUT, order.cancelReason)
    }

    @Test
    fun 수량이_1_미만이면_INVALID_REQUEST() {
        val e = assertFailsWith<DomainException> { Order.create(1L, 10L, 0, Money(5000)) }
        assertEquals(ErrorCode.INVALID_REQUEST, e.errorCode)
    }
}
