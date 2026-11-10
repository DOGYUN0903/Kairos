package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PurchaseAutoConfirmServiceTest {

    private val f = OrderFlowFixture()
    private val service = PurchaseAutoConfirmService(f.orderRepository, f.shippingService, f.properties, f.clock)

    /** 배송 완료 시각만 다르게 하려고 시계가 다른 서비스로 배송 처리한다. */
    private fun shippingServiceAt(days: Long) = OrderShippingService(f.orderRepository, f.tx, f.clockAt(f.now.minusDays(days)), f.pointEarner)

    @Test
    fun 일곱_일_지난_DELIVERED만_CONFIRMED가_된다() {
        val order = f.paid()
        val soIds = order.sellerOrders.associate { it.sellerId to it.id!! }
        listOf(1L to 8L, 2L to 6L).forEach { (sellerId, daysAgo) ->
            val s = shippingServiceAt(daysAgo)
            s.ship(sellerId, soIds.getValue(sellerId), "04", "T$sellerId")
            s.deliver(sellerId, soIds.getValue(sellerId))
        }

        assertEquals(1, service.confirmDelivered())

        val saved = f.reload(order)
        assertEquals(SellerOrderStatus.CONFIRMED, saved.findSellerOrder(soIds.getValue(1L)).status)
        assertEquals(SellerOrderStatus.DELIVERED, saved.findSellerOrder(soIds.getValue(2L)).status)
        assertEquals(OrderStatus.PAID, saved.status)
    }

    @Test
    fun 취소된_판매자_주문이_있어도_나머지가_확정되면_주문은_COMPLETED다() {
        val order = f.paid()
        val soIds = order.sellerOrders.associate { it.sellerId to it.id!! }
        f.pgCancelDone(30_000, balance = 30_000)
        f.cancelService.cancelByBuyer(f.memberId, order.id!!, order.allItems.first { it.skuId == 20L }.id!!, "변심")
        val s = shippingServiceAt(8)
        s.ship(1L, soIds.getValue(1L), "04", "T1")
        s.deliver(1L, soIds.getValue(1L))

        assertEquals(1, service.confirmDelivered())

        val saved = f.reload(order)
        assertEquals(SellerOrderStatus.CANCELED, saved.findSellerOrder(soIds.getValue(2L)).status)
        assertEquals(OrderStatus.COMPLETED, saved.status)
    }
}
