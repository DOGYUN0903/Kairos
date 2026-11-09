package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

class SellerOrder(
    val id: Long?,
    val sellerId: Long,
    val sellerName: String,
    val commissionRateBp: Int,
    status: SellerOrderStatus,
    val shippingFee: Long,
    val shippingFeePayer: ShippingFeePayer,
    carrierCode: String?,
    trackingNumber: String?,
    shippedAt: LocalDateTime?,
    deliveredAt: LocalDateTime?,
    confirmedAt: LocalDateTime?,
    val items: List<OrderItem>,
) {
    var status: SellerOrderStatus = status
        private set
    var carrierCode: String? = carrierCode
        private set
    var trackingNumber: String? = trackingNumber
        private set
    var shippedAt: LocalDateTime? = shippedAt
        private set
    var deliveredAt: LocalDateTime? = deliveredAt
        private set
    var confirmedAt: LocalDateTime? = confirmedAt
        private set

    val itemsAmount: Long get() = items.sumOf { it.lineAmount }
    val remainingItemsAmount: Long
        get() = items.filter { it.status == OrderItemStatus.ORDERED }.sumOf { it.lineAmount }
    val hasCancelRequested: Boolean get() = items.any { it.status == OrderItemStatus.CANCEL_REQUESTED }

    fun markPaid() = transit(setOf(SellerOrderStatus.PENDING_PAYMENT), SellerOrderStatus.PAID)

    fun ship(carrierCode: String, trackingNumber: String, now: LocalDateTime) {
        if (status == SellerOrderStatus.SHIPPING) {
            if (this.carrierCode == carrierCode && this.trackingNumber == trackingNumber) return
            throw DomainException(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION)
        }
        if (hasCancelRequested) throw DomainException(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION)
        transit(setOf(SellerOrderStatus.PAID), SellerOrderStatus.SHIPPING)
        this.carrierCode = carrierCode
        this.trackingNumber = trackingNumber
        this.shippedAt = now
    }

    fun deliver(now: LocalDateTime) {
        if (status == SellerOrderStatus.DELIVERED) return
        transit(setOf(SellerOrderStatus.SHIPPING), SellerOrderStatus.DELIVERED)
        deliveredAt = now
    }

    fun confirm(now: LocalDateTime) {
        if (status == SellerOrderStatus.CONFIRMED) return
        transit(setOf(SellerOrderStatus.DELIVERED), SellerOrderStatus.CONFIRMED)
        confirmedAt = now
    }

    fun cancel() = transit(setOf(SellerOrderStatus.PENDING_PAYMENT, SellerOrderStatus.PAID), SellerOrderStatus.CANCELED)

    private fun transit(from: Set<SellerOrderStatus>, to: SellerOrderStatus) {
        if (status !in from) throw DomainException(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION)
        status = to
    }
}
