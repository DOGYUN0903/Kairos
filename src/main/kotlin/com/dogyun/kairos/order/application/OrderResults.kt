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
    val productId: Long,
    val productName: String,
    val brandName: String,
    val optionDisplayName: String,
    /** 이미지가 없으면 빈 문자열 */
    val thumbnailUrl: String,
    /** 판매가 + 옵션 추가금 */
    val unitPrice: Long,
    val sellerId: Long,
    val quantity: Int,
    val lineAmount: Long,
    val productCouponDiscount: Long,
    val cartCouponAllocated: Long,
    val pointsAllocated: Long,
    val paidAmount: Long,
)

data class SellerQuote(
    val sellerId: Long,
    val sellerName: String,
    val itemsAmount: Long,
    val shippingFee: Long,
    /** BUYER | PLATFORM */
    val shippingFeePayer: String,
)

/** 주문 금액 미리보기. place 와 같은 계산이며 아무것도 저장하지 않는다. */
data class OrderQuote(
    val items: List<QuoteLine>,
    val sellerQuotes: List<SellerQuote>,
    val freeShippingThreshold: Long,
    val baseShippingFee: Long,
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
    /** 조회 시점 상품의 대표 이미지. 상품이 삭제됐거나 이미지가 없으면 null. */
    val thumbnailUrl: String?,
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
    val returnRequest: ReturnRequestSummary?,
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
    val returnableUntil: LocalDateTime?,
    val allItemsReturned: Boolean,
    val hasOpenReturn: Boolean,
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
    /** 결제 금액에서 누적 환불액을 뺀 값. 화면은 이 값을 그대로 보여준다. */
    val remainingAmount: Long,
    /** 남은 항목(ORDERED·열린 반품)의 현금 결제 몫 합 */
    val remainingPaidAmount: Long,
    /** 아직 환불되지 않은 구매자 부담 배송비 */
    val remainingBuyerShippingFee: Long,
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
    /** 첫 항목의 대표 이미지. 없으면 null. */
    val thumbnailUrl: String?,
    /** 판매자 주문마다의 상태(판매자 주문 수만큼). 주문 카드의 배송 진행 표시용. */
    val sellerOrderStatuses: List<String>,
    /** 결제 만료 시각. 상세의 expiresAt 과 같은 값. */
    val expiresAt: LocalDateTime,
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
    val returnableUntil: LocalDateTime?,
    val allItemsReturned: Boolean,
    val hasOpenReturn: Boolean,
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
    val thumbnailUrl: String?,
    val orderedAt: LocalDateTime,
)

/** 환불 미리보기. 실제 취소와 같은 계산([com.dogyun.kairos.order.domain.RefundCalculator])의 결과이며 아무것도 바꾸지 않는다. */
data class CancelPreviewResult(
    val orderItemId: Long,
    val paidAmount: Long,
    val shippingFeeRefund: Long,
    val shippingFeeDeducted: Long,
    val shippingFeeReturned: Long,
    val refundAmount: Long,
    val pointsRefund: Long,
)

data class CancelItemResult(
    val orderItemId: Long,
    /** ORDERED, CANCEL_REQUESTED, CANCELED */
    val itemStatus: String,
    val refundAmount: Long,
    val shippingFeeDeducted: Long,
    /** 이 항목 취소로 되돌린 포인트(확정 전이면 0) */
    val pointsRefund: Long,
)

/** 후기 작성 자격 조회 결과. [confirmedAt] 은 구매 확정된 `ORDERED` 항목일 때만 있다. */
data class ConfirmedItemResult(
    val orderId: Long,
    val orderItemId: Long,
    val productId: Long,
    val sellerId: Long,
    val productName: String,
    val confirmedAt: LocalDateTime?,
)

/** 정산용 확정 판매자 주문. 저장된 스냅샷 값만 담는다. */
data class ConfirmedSellerOrderResult(
    val sellerOrderId: Long,
    val sellerId: Long,
    val orderId: Long,
    val orderNo: String,
    val confirmedAt: LocalDateTime,
    val commissionRateBp: Int,
    val shippingFee: Long,
    /** 판매자 귀책 완료 반품으로 구매자에게 돌려준 발송 배송비. */
    val buyerShippingFeeRefunded: Long,
    val items: List<ConfirmedSellerOrderItemResult>,
)

data class ConfirmedSellerOrderItemResult(
    val orderItemId: Long,
    val status: String,
    val lineAmount: Long,
    val productCouponDiscount: Long,
    val productCouponIssuer: String?,
    /** 이 항목 `COMPLETED` 반품의 `sellerBorneFee`(없으면 0). */
    val sellerBorneReturnFee: Long,
)
