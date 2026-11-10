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

data class OrderItemResult(
    val orderItemId: Long,
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val optionDisplayName: String,
    val salePrice: Long,
    val extraPrice: Long,
    val quantity: Int,
    val lineAmount: Long,
    val status: String,
    val cancelActor: String?,
    val cancelReason: String?,
    val refundAmount: Long,
)

data class SellerOrderResult(
    val sellerOrderId: Long,
    val sellerId: Long,
    val sellerName: String,
    val status: String,
    val shippingFee: Long,
    val shippingFeePayer: String,
    val carrierCode: String?,
    val trackingNumber: String?,
    val shippedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val items: List<OrderItemResult>,
)

data class PaymentResult(
    val paymentKey: String?,
    val amount: Long,
    val refundedAmount: Long,
    val status: String,
)

data class ShippingAddressResult(
    val receiverName: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val memo: String?,
)

data class OrderDetail(
    val orderId: Long,
    val orderNo: String,
    val status: String,
    val itemsAmount: Long,
    val buyerShippingFee: Long,
    val totalAmount: Long,
    val freeShippingApplied: Boolean,
    val shippingFeeRecovered: Long,
    val refundedAmount: Long,
    val expiresAt: LocalDateTime,
    val paidAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val shippingAddress: ShippingAddressResult,
    val sellerOrders: List<SellerOrderResult>,
    val payment: PaymentResult?,
)

data class OrderSummary(
    val orderId: Long,
    val orderNo: String,
    val status: String,
    val totalAmount: Long,
    val itemCount: Int,
    val firstProductName: String,
    val createdAt: LocalDateTime,
)

data class SellerOrderDetail(
    val sellerOrderId: Long,
    val orderId: Long,
    val orderNo: String,
    val status: String,
    val shippingFee: Long,
    val shippingFeePayer: String,
    val carrierCode: String?,
    val trackingNumber: String?,
    val shippedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val shippingAddress: ShippingAddressResult,
    val items: List<OrderItemResult>,
    val orderedAt: LocalDateTime,
)

data class SellerOrderSummary(
    val sellerOrderId: Long,
    val orderNo: String,
    val status: String,
    val itemsAmount: Long,
    val itemCount: Int,
    val firstProductName: String,
    val orderedAt: LocalDateTime,
)

data class CancelItemResult(
    val orderItemId: Long,
    /** ORDERED, CANCEL_REQUESTED, CANCELED */
    val itemStatus: String,
    val refundAmount: Long,
    val shippingFeeDeducted: Long,
)
