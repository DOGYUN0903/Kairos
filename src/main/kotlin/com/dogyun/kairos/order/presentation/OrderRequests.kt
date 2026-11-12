package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.order.application.CancelItemResult
import com.dogyun.kairos.order.application.CancelPreviewResult
import com.dogyun.kairos.order.application.ConfirmPaymentCommand
import com.dogyun.kairos.order.application.ConfirmPaymentResult
import com.dogyun.kairos.order.application.OrderItemCommand
import com.dogyun.kairos.order.application.PlaceOrderCommand
import com.dogyun.kairos.order.application.PlaceOrderResult
import com.dogyun.kairos.order.application.ProductCouponCommand
import com.dogyun.kairos.order.application.QuoteOrderCommand
import com.dogyun.kairos.order.application.SellerOrderState
import com.dogyun.kairos.order.application.ShippingAddressCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateOrderItemRequest(
    @field:Min(1) val skuId: Long,
    @field:Min(1) @field:Max(999) val quantity: Int,
)

data class ShippingAddressRequest(
    @field:NotBlank @field:Size(max = 50) val receiverName: String,
    @field:NotBlank @field:Size(max = 20) val phone: String,
    @field:NotBlank @field:Size(max = 10) val zipCode: String,
    @field:NotBlank @field:Size(max = 200) val address1: String,
    @field:NotBlank @field:Size(max = 200) val address2: String,
    @field:Size(max = 200) val memo: String? = null,
) {
    fun toCommand() = ShippingAddressCommand(receiverName, phone, zipCode, address1, address2, memo)
}

data class ProductCouponRequest(
    @field:Positive val skuId: Long,
    @field:Positive val couponId: Long,
) {
    fun toCommand() = ProductCouponCommand(skuId, couponId)
}

data class CreateOrderRequest(
    @field:NotEmpty @field:Valid val items: List<CreateOrderItemRequest>,
    val expectedTotalAmount: Long,
    @field:Valid val shippingAddress: ShippingAddressRequest,
    @field:Valid val productCoupons: List<ProductCouponRequest> = emptyList(),
    @field:Positive val cartCouponId: Long? = null,
    @field:Min(0) val pointsToUse: Long = 0,
) {
    fun toCommand() = PlaceOrderCommand(
        items.map { OrderItemCommand(it.skuId, it.quantity) },
        expectedTotalAmount,
        shippingAddress.toCommand(),
        productCoupons.map { it.toCommand() },
        cartCouponId,
        pointsToUse,
    )
}

data class QuoteOrderRequest(
    @field:NotEmpty @field:Valid val items: List<CreateOrderItemRequest>,
    @field:Valid val productCoupons: List<ProductCouponRequest> = emptyList(),
    @field:Positive val cartCouponId: Long? = null,
    @field:Min(0) val pointsToUse: Long = 0,
) {
    fun toCommand() = QuoteOrderCommand(
        items.map { OrderItemCommand(it.skuId, it.quantity) },
        productCoupons.map { it.toCommand() },
        cartCouponId,
        pointsToUse,
    )
}

data class CreateOrderResponse(
    val orderId: Long,
    val orderNo: String,
    val totalAmount: Long,
    val expiresAt: LocalDateTime,
    val orderStatus: String,
    val paymentStatus: String,
) {
    companion object {
        fun from(result: PlaceOrderResult) = CreateOrderResponse(
            result.orderId, result.orderNo, result.totalAmount, result.expiresAt, result.orderStatus, result.paymentStatus,
        )
    }
}

data class ConfirmPaymentRequest(
    @field:NotBlank val orderNo: String,
    @field:NotBlank val paymentKey: String,
    @field:Min(0) val amount: Long,
) {
    fun toCommand() = ConfirmPaymentCommand(orderNo, paymentKey, amount)
}

data class ConfirmPaymentResponse(
    val orderId: Long,
    val orderNo: String,
    val orderStatus: String,
    val paymentStatus: String,
) {
    companion object {
        fun from(result: ConfirmPaymentResult) =
            ConfirmPaymentResponse(result.orderId, result.orderNo, result.orderStatus, result.paymentStatus)
    }
}

data class CancelItemRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class CancelItemResponse(
    val orderItemId: Long,
    val itemStatus: String,
    val refundAmount: Long,
    val shippingFeeDeducted: Long,
    val pointsRefund: Long,
) {
    companion object {
        fun from(result: CancelItemResult) =
            CancelItemResponse(result.orderItemId, result.itemStatus, result.refundAmount, result.shippingFeeDeducted, result.pointsRefund)
    }
}

data class CancelPreviewResponse(
    val orderItemId: Long,
    val paidAmount: Long,
    val shippingFeeRefund: Long,
    val shippingFeeDeducted: Long,
    val shippingFeeReturned: Long,
    val refundAmount: Long,
    val pointsRefund: Long,
) {
    companion object {
        fun from(result: CancelPreviewResult) = CancelPreviewResponse(
            result.orderItemId, result.paidAmount, result.shippingFeeRefund, result.shippingFeeDeducted,
            result.shippingFeeReturned, result.refundAmount, result.pointsRefund,
        )
    }
}

data class ShipRequest(
    @field:NotBlank @field:Size(max = 20) val carrierCode: String,
    @field:NotBlank @field:Size(max = 50) val trackingNumber: String,
)

data class SellerOrderStateResponse(
    val sellerOrderId: Long,
    val sellerOrderStatus: String,
    val orderStatus: String,
) {
    companion object {
        fun from(state: SellerOrderState) =
            SellerOrderStateResponse(state.sellerOrderId, state.sellerOrderStatus.name, state.orderStatus.name)
    }
}
