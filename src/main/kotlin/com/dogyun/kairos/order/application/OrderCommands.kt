package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.order.domain.OrderStatus

data class PlaceOrderCommand(val memberId: Long, val dealId: Long, val quantity: Int)

data class PlaceOrderResult(val orderId: Long, val status: OrderStatus, val cancelReason: CancelReason?)
