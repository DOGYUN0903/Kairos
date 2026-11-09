package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/**
 * 항목 하나를 취소할 때의 환불 내역. 환불 금액 계산은 [RefundCalculator] 한 곳에서만 한다.
 * `refundAmount = itemAmount + shippingFeeRefund - shippingFeeDeducted + shippingFeeReturned`.
 */
data class RefundPlan(
    val itemId: Long,
    val itemAmount: Long,
    val shippingFeeRefund: Long,
    val shippingFeeDeducted: Long,
    val shippingFeeReturned: Long,
) {
    val refundAmount: Long get() = itemAmount + shippingFeeRefund - shippingFeeDeducted + shippingFeeReturned
}

/** 스펙 5장. 상태는 바꾸지 않는 순수 계산이다. */
object RefundCalculator {

    fun plan(order: Order, itemId: Long, actor: CancelActor, policy: ShippingPolicy): RefundPlan {
        if (order.status != OrderStatus.PAID) throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        val item = order.findItem(itemId)
        val sellerOrder = order.sellerOrders.first { so -> so.items.any { it.id == itemId } }
        if (item.status != OrderItemStatus.ORDERED || sellerOrder.status != SellerOrderStatus.PAID) {
            throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        }
        // 다른 항목의 취소가 확인 중이면 그 결과(되돌림 포함)에 따라 이 계산이 틀어질 수 있어 한 번에 하나만 받는다.
        if (order.allItems.any { it.status == OrderItemStatus.CANCEL_REQUESTED }) {
            throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        }

        val itemAmount = item.lineAmount
        val remainingAfter = order.remainingItemsAmount - itemAmount
        val sellerOrderEmptied = sellerOrder.items.none { it.status == OrderItemStatus.ORDERED && it.id != itemId }

        // 구매자가 낸 배송비는 그 판매자 주문이 통째로 비면 돌려준다.
        val shippingFeeRefund =
            if (sellerOrderEmptied && sellerOrder.shippingFeePayer == ShippingFeePayer.BUYER) sellerOrder.shippingFee else 0L

        val everDeducted = order.allItems.sumOf { it.shippingFeeDeducted }
        val outstandingDeduction = everDeducted - order.allItems.sumOf { it.shippingFeeReturned }

        var shippingFeeDeducted = 0L
        var shippingFeeReturned = 0L
        if (remainingAfter == 0L) {
            // 마지막 취소: 차감하지 않고 앞서 차감한 배송비를 돌려준다.
            shippingFeeReturned = outstandingDeduction
        } else if (
            actor == CancelActor.BUYER && order.freeShippingApplied &&
            remainingAfter < policy.freeShippingThreshold && everDeducted == 0L
        ) {
            shippingFeeDeducted = minOf(policy.baseShippingFee, itemAmount + shippingFeeRefund)
        }

        val plan = RefundPlan(itemId, itemAmount, shippingFeeRefund, shippingFeeDeducted, shippingFeeReturned)
        check(plan.refundAmount >= 0) { "환불액은 0 이상이어야 한다: $plan" }
        check(order.refundedAmount + plan.refundAmount <= order.totalAmount) { "누적 환불액이 결제 금액을 넘는다: $plan" }
        return plan
    }
}
