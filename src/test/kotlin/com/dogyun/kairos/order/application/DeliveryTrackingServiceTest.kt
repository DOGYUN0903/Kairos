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
}
