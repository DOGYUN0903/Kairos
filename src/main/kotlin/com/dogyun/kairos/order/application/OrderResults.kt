package com.dogyun.kairos.order.application

import java.time.LocalDateTime

data class PlaceOrderResult(
    val orderId: Long,
    val orderNo: String,
    val totalAmount: Long,
    val expiresAt: LocalDateTime,
    /** 0원 주문이면 PAID, 아니면 PENDING_PAYMENT */
    val orderStatus: String,
    /** 0원 주문이면 APPROVED, 아니면 READY */
    val paymentStatus: String,
)

data class QuoteLine(
    val skuId: Long,
    val quantity: Int,
    val lineAmount: Long,
    val productCouponDiscount: Long,
    val cartCouponAllocated: Long,
    val pointsAllocated: Long,
    val paidAmount: Long,
)

/** 주문 금액 미리보기. place 와 같은 계산이며 아무것도 저장하지 않는다. */
data class OrderQuote(
    val items: List<QuoteLine>,
    val itemsAmount: Long,
    val productCouponDiscount: Long,
    val cartCouponDiscount: Long,
    val pointsUsed: Long,
    val buyerShippingFee: Long,
    val totalAmount: Long,
    /** 이 쿠폰 조합에서 쓸 수 있는 포인트 최대치(잔액이 최소 사용 잔액 미만이면 0) */
    val maxUsablePoints: Long,
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
    val productCouponId: Long?,
    val productCouponDiscount: Long,
    /** PLATFORM, SELLER, 쿠폰 없으면 null */
    val productCouponIssuer: String?,
    val cartCouponAllocated: Long,
    val pointsAllocated: Long,
    val paidAmount: Long,
    val pointsRefunded: Long,
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
    val productCouponDiscount: Long,
    val cartCouponId: Long?,
    val cartCouponDiscount: Long,
    val pointsUsed: Long,
    val pointsRefunded: Long,
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
