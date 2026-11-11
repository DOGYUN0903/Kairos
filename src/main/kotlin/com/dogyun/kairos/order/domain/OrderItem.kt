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
    returnRequest: ReturnRequest?,
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
    var returnRequest: ReturnRequest? = returnRequest
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

    /** 처음 요청하는 반품. 이미 반품 행이 있으면(거절·관리자 취소 포함) 새로 만들지 못한다. 철회한 반품은 [reopenReturn] 으로 되살린다. */
    fun requestReturn(request: ReturnRequest) {
        if (status != OrderItemStatus.ORDERED) throw DomainException(ErrorCode.RETURN_NOT_ELIGIBLE)
        if (returnRequest != null) throw DomainException(ErrorCode.RETURN_ALREADY_REQUESTED)
        returnRequest = request
        status = OrderItemStatus.RETURN_REQUESTED
    }

    fun reopenReturn(reason: ReturnReason, detail: String?, now: LocalDateTime) {
        if (status != OrderItemStatus.ORDERED) throw DomainException(ErrorCode.RETURN_NOT_ELIGIBLE)
        returnOrThrow().reopen(reason, detail, now)
        status = OrderItemStatus.RETURN_REQUESTED
    }

    fun approveReturn(by: DecidedBy, now: LocalDateTime) {
        returnOrThrow().approve(by, now)
        status = OrderItemStatus.RETURN_APPROVED
    }

    fun rejectReturn(reason: String, now: LocalDateTime) {
        returnOrThrow().reject(reason, now)
        status = OrderItemStatus.ORDERED
    }

    fun withdrawReturn() {
        returnOrThrow().withdraw()
        status = OrderItemStatus.ORDERED
    }

    fun cancelReturnByAdmin() {
        returnOrThrow().cancelByAdmin()
        status = OrderItemStatus.ORDERED
    }

    fun startReturnRefund(plan: ReturnRefundPlan, now: LocalDateTime) {
        val request = returnOrThrow()
        if (status != OrderItemStatus.RETURN_APPROVED) throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
        if (plan.itemId != id || plan.paidAmount != paidAmount || plan.pointsRefund != pointsAllocated) throw DomainException(ErrorCode.RETURN_INVALID)
        request.startRefund(plan, now)
        status = OrderItemStatus.RETURN_REFUNDING
    }

    fun revertReturnRefund() {
        returnOrThrow().revertRefund()
        status = OrderItemStatus.RETURN_APPROVED
    }

    fun completeReturn(now: LocalDateTime, pointsRefund: Long) {
        val request = returnOrThrow()
        if (status != OrderItemStatus.RETURN_REFUNDING) throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
        request.complete(now)
        status = OrderItemStatus.RETURNED
        pointsRefunded = pointsRefund
    }

    /** 수거 확인 때 저장해 둔 값에서 반품 환불 계획을 복원한다. */
    fun toReturnRefundPlan(): ReturnRefundPlan = returnOrThrow().toRefundPlan(paidAmount, productCouponId)

    private fun returnOrThrow(): ReturnRequest = returnRequest ?: throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
}
