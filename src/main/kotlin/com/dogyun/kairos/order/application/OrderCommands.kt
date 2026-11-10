package com.dogyun.kairos.order.application

data class OrderItemCommand(val skuId: Long, val quantity: Int)

data class ShippingAddressCommand(
    val receiverName: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val memo: String?,
)

/** 항목(SKU) 하나에 거는 상품 쿠폰. */
data class ProductCouponCommand(val skuId: Long, val couponId: Long)

data class PlaceOrderCommand(
    val items: List<OrderItemCommand>,
    val expectedTotalAmount: Long,
    val shippingAddress: ShippingAddressCommand,
    val productCoupons: List<ProductCouponCommand> = emptyList(),
    val cartCouponId: Long? = null,
    val pointsToUse: Long = 0,
)

data class QuoteOrderCommand(
    val items: List<OrderItemCommand>,
    val productCoupons: List<ProductCouponCommand> = emptyList(),
    val cartCouponId: Long? = null,
    val pointsToUse: Long = 0,
)

data class ConfirmPaymentCommand(val orderNo: String, val paymentKey: String, val amount: Long)
