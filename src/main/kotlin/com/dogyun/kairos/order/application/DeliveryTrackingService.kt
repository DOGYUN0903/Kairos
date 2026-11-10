package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.SellerOrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 배송 중인 판매자 주문을 택배사에 조회해 배송 완료를 반영한다. 택배사 호출은 트랜잭션 밖이다. */
@Service
class DeliveryTrackingService(
    private val orderRepository: OrderRepository,
    private val deliveryTracker: DeliveryTracker,
    private val orderShippingService: OrderShippingService,
) {

    /** 배송 완료로 바꾼 판매자 주문 수. */
    fun pollShipping(limit: Int = 100): Int =
        orderRepository.findSellerOrderIdsByStatus(SellerOrderStatus.SHIPPING, limit).count { sellerOrderId ->
            try {
                poll(sellerOrderId)
            } catch (e: RuntimeException) {
                log.error("배송 조회 처리에 실패했다: sellerOrderId={}", sellerOrderId, e)
                false
            }
        }

    private fun poll(sellerOrderId: Long): Boolean {
        val order = orderRepository.findBySellerOrderId(sellerOrderId) ?: return false
        val sellerOrder = order.findSellerOrder(sellerOrderId)
        val carrierCode = sellerOrder.carrierCode ?: return false
        val trackingNumber = sellerOrder.trackingNumber ?: return false
        if (deliveryTracker.track(carrierCode, trackingNumber) != TrackingResult.DELIVERED) return false
        orderShippingService.deliverBySystem(sellerOrderId)
        return true
    }

    private companion object {
        val log = LoggerFactory.getLogger(DeliveryTrackingService::class.java)
    }
}
