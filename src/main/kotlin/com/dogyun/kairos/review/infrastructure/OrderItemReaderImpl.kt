package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.review.application.ConfirmedOrderItem
import com.dogyun.kairos.review.application.OrderItemReader
import org.springframework.stereotype.Component

@Component
class OrderItemReaderImpl(private val orderQueryService: OrderQueryService) : OrderItemReader {
    override fun findConfirmedItem(memberId: Long, orderItemId: Long): ConfirmedOrderItem? =
        orderQueryService.findConfirmedItem(memberId, orderItemId)?.let {
            ConfirmedOrderItem(it.orderId, it.orderItemId, it.productId, it.sellerId, it.productName, it.confirmedAt)
        }
}
