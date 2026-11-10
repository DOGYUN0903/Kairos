package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderExpiryServiceTest {

    private val f = OrderFlowFixture()

    /** 만료 시각(15분)이 지난 뒤의 시계로 만든 서비스. */
    private val service = OrderExpiryService(
        f.orderRepository, f.paymentGateway, f.stockManager, f.tx, f.clockAt(f.now.plusMinutes(16)),
    )

    @Test
    fun 만료된_READY_주문은_CANCELED_결제_FAILED_재고_복구() {
        val order = f.place()

        assertEquals(1, service.expirePendingOrders())

        assertEquals(OrderStatus.CANCELED, f.reload(order).status)
        assertEquals("FAILED", f.paymentGateway.get(order.id!!).status)
        assertEquals("EXPIRED", f.paymentGateway.get(order.id!!).failReason)
        assertEquals(10, f.stockManager.stock[10L])
        assertEquals(10, f.stockManager.stock[20L])
    }

    @Test
    fun IN_PROGRESS나_UNKNOWN_결제의_주문은_건너뛴다() {
        val inProgress = f.place()
        f.startPayment(inProgress, "pay-1")
        val unknown = f.place()
        f.startPayment(unknown, "unknown-1", unknown = true)

        assertEquals(0, service.expirePendingOrders())

        assertEquals(OrderStatus.PENDING_PAYMENT, f.reload(inProgress).status)
        assertEquals(OrderStatus.PENDING_PAYMENT, f.reload(unknown).status)
        assertEquals("IN_PROGRESS", f.paymentGateway.get(inProgress.id!!).status)
        assertEquals("UNKNOWN", f.paymentGateway.get(unknown.id!!).status)
        assertEquals(8, f.stockManager.stock[10L])
    }

    @Test
    fun 이미_PAID가_된_주문은_건너뛴다() {
        val order = f.paid()

        assertEquals(0, service.expirePendingOrders())

        assertEquals(OrderStatus.PAID, f.reload(order).status)
        assertEquals("APPROVED", f.paymentGateway.get(order.id!!).status)
        assertEquals(9, f.stockManager.stock[10L])
    }
}
