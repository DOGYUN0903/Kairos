package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.order.application.PlaceOrderCommand
import com.dogyun.kairos.order.application.PlaceOrderResult
import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.order.domain.OrderStatus
import jakarta.validation.constraints.Min

data class CreateOrderRequest(
    @field:Min(1) val dealId: Long,
    @field:Min(1) val quantity: Int,
) {
    fun toCommand(memberId: Long) = PlaceOrderCommand(memberId, dealId, quantity)
}

data class CreateOrderResponse(val orderId: Long, val status: OrderStatus, val cancelReason: CancelReason?) {
    companion object {
        fun from(r: PlaceOrderResult) = CreateOrderResponse(r.orderId, r.status, r.cancelReason)
    }
}
