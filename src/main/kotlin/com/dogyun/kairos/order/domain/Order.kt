package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money

/** member/deal 은 ID 로만 참조한다. amount 는 주문 시점 단가 x 수량. */
class Order private constructor(
    val id: Long?,
    val memberId: Long,
    val dealId: Long,
    val quantity: Int,
    val amount: Money,
    status: OrderStatus,
    cancelReason: CancelReason?,
) {
    var status: OrderStatus = status
        private set
    var cancelReason: CancelReason? = cancelReason
        private set

    fun markPaid() {
        check(status == OrderStatus.CREATED) { "CREATED 주문만 결제 완료로 바꿀 수 있습니다: id=$id, status=$status" }
        status = OrderStatus.PAID
    }

    fun cancel(reason: CancelReason) {
        if (status == OrderStatus.CANCELLED) throw DomainException(ErrorCode.ORDER_ALREADY_CANCELLED)
        status = OrderStatus.CANCELLED
        cancelReason = reason
    }

    companion object {
        fun create(memberId: Long, dealId: Long, quantity: Int, unitPrice: Money): Order {
            if (quantity < 1) throw DomainException(ErrorCode.INVALID_REQUEST)
            return Order(null, memberId, dealId, quantity, unitPrice * quantity, OrderStatus.CREATED, null)
        }

        fun restore(
            id: Long,
            memberId: Long,
            dealId: Long,
            quantity: Int,
            amount: Money,
            status: OrderStatus,
            cancelReason: CancelReason?,
        ) = Order(id, memberId, dealId, quantity, amount, status, cancelReason)
    }
}
