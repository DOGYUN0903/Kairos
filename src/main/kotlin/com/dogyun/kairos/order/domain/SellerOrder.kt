package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Duration
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
        get() = items.filter { it.status.isRemaining }.sumOf { it.lineAmount }
    val remainingPaidAmount: Long
        get() = items.filter { it.status.isRemaining }.sumOf { it.paidAmount }
    /** 구매 확정 적립 기준: `ORDERED` 항목의 `paidAmount` 합(배송비 제외). 열린 반품이 있으면 확정이 막혀 남은 항목과 차이가 없다. */
    val confirmedPaidAmount: Long get() = items.filter { it.status == OrderItemStatus.ORDERED }.sumOf { it.paidAmount }
    val hasCancelRequested: Boolean get() = items.any { it.status == OrderItemStatus.CANCEL_REQUESTED }
    val hasOpenReturn: Boolean get() = items.any { it.status.isOpenReturn }

    /** 구매 확정 판매자 주문에서 `ORDERED` 항목이 없고 `RETURNED` 가 하나 이상이다. */
    val allItemsReturned: Boolean
        get() = status == SellerOrderStatus.CONFIRMED && items.none { it.status == OrderItemStatus.ORDERED } &&
            items.any { it.status == OrderItemStatus.RETURNED }

    private val completedReturns: List<ReturnRequest>
        get() = items.mapNotNull { it.returnRequest }.filter { it.status == ReturnStatus.COMPLETED }

    /** 완료된 반품에서 구매자에게 돌려준 구매자 부담 배송비 합. */
    val buyerShippingFeeRefunded: Long get() = completedReturns.sumOf { it.shippingFeeRefund }
    val completedReturnFees: Long get() = completedReturns.sumOf { it.returnFee }
    val sellerBorneReturnFees: Long get() = completedReturns.sumOf { it.sellerBorneFee }

    /** 반품 가능 마감 시각. 배송 완료 상태일 때만 있다. */
    fun returnableUntil(window: Duration): LocalDateTime? = if (status == SellerOrderStatus.DELIVERED) deliveredAt?.plus(window) else null

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
        if (hasOpenReturn) throw DomainException(ErrorCode.SELLER_ORDER_HAS_OPEN_RETURN)
        transit(setOf(SellerOrderStatus.DELIVERED), SellerOrderStatus.CONFIRMED)
        confirmedAt = now
    }

    fun cancel() = transit(setOf(SellerOrderStatus.PENDING_PAYMENT, SellerOrderStatus.PAID), SellerOrderStatus.CANCELED)

    private fun transit(from: Set<SellerOrderStatus>, to: SellerOrderStatus) {
        if (status !in from) throw DomainException(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION)
        status = to
    }
}
