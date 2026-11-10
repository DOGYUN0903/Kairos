package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/**
 * 항목 하나를 취소할 때의 환불 내역. 환불 금액 계산은 [RefundCalculator] 한 곳에서만 한다.
 * `refundAmount = paidAmount + shippingFeeRefund - shippingFeeDeducted + shippingFeeReturned`.
 */
data class RefundPlan(
    val itemId: Long,
    val paidAmount: Long,
    val shippingFeeRefund: Long,
    val shippingFeeDeducted: Long,
    val shippingFeeReturned: Long,
    val pointsRefund: Long,
    val productCouponReturn: Long?,
) {
    val refundAmount: Long get() = paidAmount + shippingFeeRefund - shippingFeeDeducted + shippingFeeReturned
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

        // 무료배송 기준은 할인 전 금액(lineAmount)으로 판정한다(스펙 4-3). 환불 현금은 실제로 낸 paidAmount 다.
        val remainingAfter = order.remainingItemsAmount - item.lineAmount
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
            remainingAfter < policy.freeShippingThreshold && everDeducted == 0L &&
            // 이 취소로 처음 기준 아래로 떨어질 때만. 판매자·관리자 취소로 이미 깨졌다면 구매자 책임이 아니다.
            order.remainingItemsAmount >= policy.freeShippingThreshold
        ) {
            shippingFeeDeducted = minOf(policy.baseShippingFee, item.paidAmount + shippingFeeRefund)
        }

        val plan = RefundPlan(
            itemId, item.paidAmount, shippingFeeRefund, shippingFeeDeducted, shippingFeeReturned,
            pointsRefund = item.pointsAllocated, productCouponReturn = item.productCouponId,
        )
        check(plan.refundAmount >= 0) { "환불액은 0 이상이어야 한다: $plan" }
        check(order.refundedAmount + plan.refundAmount <= order.totalAmount) { "누적 환불액이 결제 금액을 넘는다: $plan" }
        check(order.pointsRefunded + plan.pointsRefund <= order.pointsUsed) { "누적 포인트 되돌림이 사용액을 넘는다: $plan" }
        return plan
    }
}
