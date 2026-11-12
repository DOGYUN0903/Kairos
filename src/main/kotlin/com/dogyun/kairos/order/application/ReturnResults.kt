package com.dogyun.kairos.order.application

import java.time.LocalDateTime

/** 수거 확인 마무리(tx2)의 결과. 정리 작업(PG 조회 대사)도 같은 값을 쓴다. */
enum class ReturnFinish { RETURNED, PENDING, REVERTED }

data class ReturnRequestResult(
    val returnId: Long,
    val orderId: Long,
    val sellerOrderId: Long,
    val orderItemId: Long,
    val productName: String,
    val quantity: Int,
    val reason: String,
    val feeBearer: String,
    val status: String,
    val returnFee: Long,
    val outboundFeeRecovered: Long,
    val shippingFeeRefund: Long,
    val sellerBorneFee: Long,
    val refundAmount: Long,
    val pointsRefund: Long,
    val rejectReason: String?,
    val requestedAt: LocalDateTime,
    val decidedAt: LocalDateTime?,
    val decidedBy: String?,
    val receivedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)

/** 주문 상세 항목에 붙는 반품 요약. */
data class ReturnRequestSummary(
    val returnId: Long,
    val reason: String,
    val status: String,
    val feeBearer: String,
    val refundAmount: Long,
    val returnFee: Long,
    val requestedAt: LocalDateTime,
)

/** 반품 요청 전 예상 환불. 수거 확인 때 실제 환불과 같은 계산이지만 그 사이 주문 상태가 바뀌면 달라질 수 있다. */
data class ReturnPreviewResult(
    val orderItemId: Long,
    val reason: String,
    val feeBearer: String,
    val paidAmount: Long,
    val returnFee: Long,
    val outboundFeeRecovered: Long,
    val shippingFeeRefund: Long,
    val refundAmount: Long,
    val pointsRefund: Long,
    val productCouponReturnable: Boolean,
)
