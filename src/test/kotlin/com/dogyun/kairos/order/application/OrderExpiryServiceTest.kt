package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderExpiryServiceTest {

    private val f = OrderFlowFixture()

    /** 만료 시각(15분)이 지난 뒤의 시계로 만든 서비스. */
    private val service = OrderExpiryService(
        f.orderRepository, f.paymentGateway, f.stockManager, f.benefitReverter, f.tx, f.clockAt(f.now.plusMinutes(16)),
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

    @Test
    fun 앞의_주문이_계속_남아도_뒤의_READY_주문까지_만료한다() {
        val stuck = (1..3).map { f.place().also { o -> f.startPayment(o, "pay-${o.id}") } }
        val ready = f.place()

        assertEquals(1, service.expirePendingOrders(limit = 2))

        stuck.forEach { assertEquals(OrderStatus.PENDING_PAYMENT, f.reload(it).status) }
        assertEquals(OrderStatus.CANCELED, f.reload(ready).status)
    }

    @Test
    fun 만료되면_revertAll이_재고_복구보다_먼저_불린다() {
        val order = f.place()

        assertEquals(1, service.expirePendingOrders())

        assertEquals(listOf(FakeBenefitReverter.RevertAll(order.id!!, f.memberId, emptySet(), 0)), f.benefitReverter.revertAllCalls)
        assertEquals(listOf("revertAll", "restore"), f.stockManager.events)
    }

    @Test
    fun 건너뛴_주문은_revertAll을_부르지_않는다() {
        f.paid()

        assertEquals(0, service.expirePendingOrders())

        assertEquals(emptyList(), f.benefitReverter.revertAllCalls)
    }
}
