package com.dogyun.kairos.order.application

import java.time.LocalDateTime

data class PlaceOrderResult(
    val orderId: Long,
    val orderNo: String,
    val totalAmount: Long,
    val expiresAt: LocalDateTime,
)

data class ConfirmPaymentResult(
    val orderId: Long,
    val orderNo: String,
    val orderStatus: String,
    val paymentStatus: String,
)
