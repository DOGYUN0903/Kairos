package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.SellerOrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryTrackingServiceTest {

    private val f = OrderFlowFixture()
    private val tracker = FakeDeliveryTracker()
    private val service = DeliveryTrackingService(f.orderRepository, tracker, f.shippingService)

    @Test
    fun DELIVERED면_판매자_주문이_DELIVERED가_되고_IN_TRANSIT_UNAVAILABLE은_그대로다() {
        val order = f.paid()
        val soIds = order.sellerOrders.associate { it.sellerId to it.id!! }
        f.shippingService.ship(1L, soIds.getValue(1L), "04", "A-D")
        f.shippingService.ship(2L, soIds.getValue(2L), "04", "B")
        tracker.results["A-D"] = TrackingResult.DELIVERED
        tracker.results["B"] = TrackingResult.UNAVAILABLE

        assertEquals(1, service.pollShipping())

        val saved = f.reload(order)
        assertEquals(SellerOrderStatus.DELIVERED, saved.findSellerOrder(soIds.getValue(1L)).status)
        assertEquals(SellerOrderStatus.SHIPPING, saved.findSellerOrder(soIds.getValue(2L)).status)

        tracker.results["B"] = TrackingResult.IN_TRANSIT
        assertEquals(0, service.pollShipping())
        assertEquals(SellerOrderStatus.SHIPPING, f.reload(order).findSellerOrder(soIds.getValue(2L)).status)
    }

    @Test
    fun 앞의_배송중_주문이_계속_남아도_뒤의_주문까지_조회한다() {
        val orders = (1..3).map { f.paid() }
        // 주문마다 판매자 주문 둘(판매자 1, 2). 송장 번호는 판매자 주문 순서대로 T0.. 로 붙인다.
        val sellerOrders = orders.flatMap { o -> o.sellerOrders.map { it.sellerId to it.id!! } }
        sellerOrders.forEachIndexed { i, (sellerId, soId) -> f.shippingService.ship(sellerId, soId, "04", "T$i") }
        val lastIndex = sellerOrders.size - 1
        tracker.results["T$lastIndex"] = TrackingResult.DELIVERED

        assertEquals(1, service.pollShipping(limit = 2))

        assertEquals(sellerOrders.size, tracker.calls.size)
        val last = f.reload(orders.last()).findSellerOrder(sellerOrders[lastIndex].second)
        assertEquals(SellerOrderStatus.DELIVERED, last.status)
    }
}
