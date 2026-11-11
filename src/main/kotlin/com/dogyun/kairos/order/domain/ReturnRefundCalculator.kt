package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 반품 수수료 설정. [returnFee] 는 회수 구간 반품비, [outboundFee] 는 무료배송이 깨질 때 회수하는 발송 구간 배송비. */
data class ReturnFeePolicy(val returnFee: Long, val outboundFee: Long)

/**
 * 반품 환불 내역. `refundAmount = paidAmount - returnFee - outboundFeeRecovered + shippingFeeRefund`.
 * 계산은 반품 환불 계산기(Task 3)가 한 곳에서 한다.
 */
data class ReturnRefundPlan(
    val itemId: Long,
    val paidAmount: Long,
    val returnFee: Long,
    val outboundFeeRecovered: Long,
    val shippingFeeRefund: Long,
    val pointsRefund: Long,
    val productCouponReturn: Long?,
    val sellerBorneFee: Long,
) {
    val refundAmount: Long get() = paidAmount - returnFee - outboundFeeRecovered + shippingFeeRefund
}

/** 스펙 5장. 반품 환불 금액 계산은 이 한 곳에서만 한다. 상태는 바꾸지 않는 순수 계산이다. */
object ReturnRefundCalculator {

    fun plan(order: Order, returnId: Long, fees: ReturnFeePolicy): ReturnRefundPlan {
        val (sellerOrder, item) = order.findReturn(returnId)
        val request = checkNotNull(item.returnRequest)
        if (order.status != OrderStatus.PAID || sellerOrder.status != SellerOrderStatus.DELIVERED ||
            request.status != ReturnStatus.APPROVED || item.status != OrderItemStatus.RETURN_APPROVED
        ) {
            throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
        }
        // 다른 항목의 취소 확인·반품 환불이 진행 중이면 그 결과에 따라 이 계산이 틀어질 수 있어 한 번에 하나만 받는다.
        if (order.allItems.any {
                it.id != item.id && (it.status == OrderItemStatus.CANCEL_REQUESTED || it.status == OrderItemStatus.RETURN_REFUNDING)
            }
        ) {
            throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
        }

        val paid = item.paidAmount
        val sellerOrderEmptied = sellerOrder.items.none { it.id != item.id && it.status.isRemaining }
        val plan = when (request.reason) {
            ReturnReason.CHANGE_OF_MIND -> {
                // 구매자 부담 반품비는 현금 환불에서만 뺀다. 회수 구간을 먼저, 남는 범위에서 발송 구간을 받는다.
                val returnFee = minOf(fees.returnFee, paid)
                // 발송 구간 회수는 취소 차감과 합쳐 주문당 한 번(이중 청구 방지).
                val outbound =
                    if (order.freeShippingApplied && order.outboundFeeRecoveredEver == 0L) minOf(fees.outboundFee, paid - returnFee) else 0L
                ReturnRefundPlan(
                    item.id!!, paid, returnFee, outbound, shippingFeeRefund = 0,
                    pointsRefund = item.pointsAllocated, productCouponReturn = item.productCouponId, sellerBorneFee = 0,
                )
            }
            ReturnReason.DEFECTIVE, ReturnReason.WRONG_DELIVERY -> {
                val shippingFeeRefund =
                    if (sellerOrderEmptied && sellerOrder.shippingFeePayer == ShippingFeePayer.BUYER) sellerOrder.shippingFee else 0L
                ReturnRefundPlan(
                    item.id!!, paid, returnFee = 0, outboundFeeRecovered = 0, shippingFeeRefund = shippingFeeRefund,
                    pointsRefund = item.pointsAllocated, productCouponReturn = item.productCouponId, sellerBorneFee = fees.returnFee,
                )
            }
        }
        check(plan.refundAmount >= 0) { "환불액은 0 이상이어야 한다: $plan" }
        check(order.refundedAmount + plan.refundAmount <= order.totalAmount) { "누적 환불액이 결제 금액을 넘는다: $plan" }
        check(order.pointsRefunded + plan.pointsRefund <= order.pointsUsed) { "누적 포인트 되돌림이 사용액을 넘는다: $plan" }
        // 발송 구간 회수는 주문당 한 번. 이미 회수했으면(취소 차감은 ShippingPolicy 금액이라 fees.outboundFee 와 다를 수 있다) 이번 회수는 0이어야 한다.
        check(order.outboundFeeRecoveredEver == 0L || plan.outboundFeeRecovered == 0L) { "발송 구간 회수가 한 번을 넘는다: $plan" }
        check(plan.outboundFeeRecovered <= fees.outboundFee) { "발송 구간 회수가 설정 금액을 넘는다: $plan" }
        return plan
    }
}
