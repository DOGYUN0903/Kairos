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

data class PlaceOrderCommand(
    val items: List<OrderItemCommand>,
    val expectedTotalAmount: Long,
    val shippingAddress: ShippingAddressCommand,
)
