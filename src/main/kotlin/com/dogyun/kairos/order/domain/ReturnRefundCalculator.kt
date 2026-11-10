package com.dogyun.kairos.order.domain

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
