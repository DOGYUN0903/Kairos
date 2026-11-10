package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.sweepByKeyset
import com.dogyun.kairos.order.domain.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/** 배송 완료 후 [OrderProperties.autoConfirmAfter] 가 지난 판매자 주문을 자동으로 구매 확정한다. */
@Service
class PurchaseAutoConfirmService(
    private val orderRepository: OrderRepository,
    private val orderShippingService: OrderShippingService,
    private val properties: OrderProperties,
    private val clock: Clock,
) {

    /** 확정 처리를 시도한 판매자 주문 수(이미 다른 흐름이 처리한 건은 no-op 이다). */
    fun confirmDelivered(limit: Int = 100): Int {
        val threshold = LocalDateTime.now(clock).minus(properties.autoConfirmAfter)
        return sweepByKeyset(limit, { it }, { after -> orderRepository.findSellerOrderIdsDeliveredBefore(threshold, after, limit) }) { sellerOrderId ->
            try {
                orderShippingService.autoConfirm(sellerOrderId)
                true
            } catch (e: RuntimeException) {
                log.error("자동 구매 확정에 실패했다: sellerOrderId={}", sellerOrderId, e)
                false
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PurchaseAutoConfirmService::class.java)
    }
}
