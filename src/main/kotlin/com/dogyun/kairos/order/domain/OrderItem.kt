package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

/** 주문 시점 스냅샷(상품명·브랜드명·옵션명·가격)을 복사해 가진다. 이후 상품이 바뀌어도 변하지 않는다. */
class OrderItem(
    val id: Long?,
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val optionDisplayName: String,
    val salePrice: Long,
    val extraPrice: Long,
    val quantity: Int,
    status: OrderItemStatus,
    cancelActor: CancelActor?,
    cancelReason: String?,
    refundAmount: Long,
    shippingFeeDeducted: Long,
    shippingFeeReturned: Long,
    canceledAt: LocalDateTime?,
    val productCouponId: Long?,
    val productCouponDiscount: Long,
    val productCouponIssuer: CouponIssuer?,
    val cartCouponAllocated: Long,
    val pointsAllocated: Long,
    /** 저장값. `lineAmount - productCouponDiscount - cartCouponAllocated - pointsAllocated` 를 만들 때 계산해 넣고 복원 시 그대로 읽는다. */
    val paidAmount: Long,
    pointsRefunded: Long,
) {
    var status: OrderItemStatus = status
        private set
    var cancelActor: CancelActor? = cancelActor
        private set
    var cancelReason: String? = cancelReason
        private set
    var refundAmount: Long = refundAmount
        private set
    var shippingFeeDeducted: Long = shippingFeeDeducted
        private set
    var shippingFeeReturned: Long = shippingFeeReturned
        private set
    var canceledAt: LocalDateTime? = canceledAt
        private set
    var pointsRefunded: Long = pointsRefunded
        private set

    val unitPrice: Long get() = salePrice + extraPrice
    val lineAmount: Long get() = unitPrice * quantity

    fun requestCancel(actor: CancelActor, reason: String?, plan: RefundPlan) {
        if (status != OrderItemStatus.ORDERED) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        if (plan.itemId != id || plan.paidAmount != paidAmount || plan.pointsRefund != pointsAllocated) throw DomainException(ErrorCode.ORDER_INVALID)
        status = OrderItemStatus.CANCEL_REQUESTED
        cancelActor = actor
        cancelReason = reason
        refundAmount = plan.refundAmount
        shippingFeeDeducted = plan.shippingFeeDeducted
        shippingFeeReturned = plan.shippingFeeReturned
    }

    /** 요청 시 저장해 둔 값에서 환불 계획을 복원한다(PG 취소 후 확정 단계에서 쓴다). */
    fun toRefundPlan(): RefundPlan = RefundPlan(
        itemId = checkNotNull(id) { "저장되지 않은 항목" },
        paidAmount = paidAmount,
        shippingFeeRefund = refundAmount - paidAmount + shippingFeeDeducted - shippingFeeReturned,
        shippingFeeDeducted = shippingFeeDeducted,
        shippingFeeReturned = shippingFeeReturned,
        pointsRefund = pointsAllocated,
        productCouponReturn = productCouponId,
    )

    fun confirmCancel(now: LocalDateTime, pointsRefund: Long = pointsAllocated) {
        if (status != OrderItemStatus.CANCEL_REQUESTED) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        status = OrderItemStatus.CANCELED
        pointsRefunded = pointsRefund
        canceledAt = now
    }

    fun revertCancelRequest() {
        if (status != OrderItemStatus.CANCEL_REQUESTED) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        status = OrderItemStatus.ORDERED
        cancelActor = null
        cancelReason = null
        refundAmount = 0
        shippingFeeDeducted = 0
        shippingFeeReturned = 0
    }

    /** 결제 전 만료·실패로 닫을 때. 환불은 없다. */
    fun cancelBySystem() {
        if (status != OrderItemStatus.ORDERED) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        status = OrderItemStatus.CANCELED
        cancelActor = CancelActor.SYSTEM
    }
}
