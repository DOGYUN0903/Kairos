package com.dogyun.kairos.payment.application

data class PaymentSnapshot(
    val paymentId: Long,
    val orderId: Long,
    val orderNo: String,
    val paymentKey: String?,
    val amount: Long,
    val refundedAmount: Long,
    val status: String,
    val failReason: String?,
)
