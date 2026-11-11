package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

enum class FeeBearer { BUYER, SELLER }

enum class ReturnReason(val feeBearer: FeeBearer) {
    CHANGE_OF_MIND(FeeBearer.BUYER),
    DEFECTIVE(FeeBearer.SELLER),
    WRONG_DELIVERY(FeeBearer.SELLER),
}

enum class ReturnStatus { REQUESTED, APPROVED, REFUNDING, COMPLETED, REJECTED, WITHDRAWN, CANCELED_BY_ADMIN }

enum class DecidedBy { SELLER, SYSTEM, ADMIN }

/** 주문 항목 하나의 반품. `Order` 애그리거트 안에서 [OrderItem] 과 함께, 주문 행 락 아래에서만 바뀐다. */
class ReturnRequest(
    val id: Long?,
    val orderId: Long,
    val sellerOrderId: Long,
    val orderItemId: Long,
    val memberId: Long,
    val sellerId: Long,
    reason: ReturnReason,
    detail: String?,
    status: ReturnStatus,
    returnFee: Long,
    outboundFeeRecovered: Long,
    shippingFeeRefund: Long,
    sellerBorneFee: Long,
    refundAmount: Long,
    pointsRefund: Long,
    rejectReason: String?,
    requestedAt: LocalDateTime,
    decidedAt: LocalDateTime?,
    decidedBy: DecidedBy?,
    receivedAt: LocalDateTime?,
    completedAt: LocalDateTime?,
) {
    var reason: ReturnReason = reason
        private set
    var detail: String? = detail
        private set
    var status: ReturnStatus = status
        private set
    var returnFee: Long = returnFee
        private set
    var outboundFeeRecovered: Long = outboundFeeRecovered
        private set
    var shippingFeeRefund: Long = shippingFeeRefund
        private set
    var sellerBorneFee: Long = sellerBorneFee
        private set
    var refundAmount: Long = refundAmount
        private set
    var pointsRefund: Long = pointsRefund
        private set
    var rejectReason: String? = rejectReason
        private set
    var requestedAt: LocalDateTime = requestedAt
        private set
    var decidedAt: LocalDateTime? = decidedAt
        private set
    var decidedBy: DecidedBy? = decidedBy
        private set
    var receivedAt: LocalDateTime? = receivedAt
        private set
    var completedAt: LocalDateTime? = completedAt
        private set

    val feeBearer: FeeBearer get() = reason.feeBearer
    val isOpen: Boolean get() = status == ReturnStatus.REQUESTED || status == ReturnStatus.APPROVED || status == ReturnStatus.REFUNDING

    /** 철회한 반품을 같은 행에서 다시 요청한다. */
    fun reopen(reason: ReturnReason, detail: String?, now: LocalDateTime) {
        if (status != ReturnStatus.WITHDRAWN) throw DomainException(ErrorCode.RETURN_ALREADY_REQUESTED)
        validateDetail(detail)
        this.reason = reason
        this.detail = detail
        status = ReturnStatus.REQUESTED
        requestedAt = now
        clearAmounts()
        rejectReason = null
        decidedAt = null
        decidedBy = null
        completedAt = null
    }

    fun approve(by: DecidedBy, now: LocalDateTime) {
        when (status) {
            ReturnStatus.APPROVED -> return
            ReturnStatus.REQUESTED -> Unit
            ReturnStatus.REJECTED -> if (by != DecidedBy.ADMIN) throw invalidTransition()
            else -> throw invalidTransition()
        }
        status = ReturnStatus.APPROVED
        decidedAt = now
        decidedBy = by
        rejectReason = null
    }

    fun reject(reason: String, now: LocalDateTime) {
        if (status == ReturnStatus.REJECTED) return
        if (status != ReturnStatus.REQUESTED) throw invalidTransition()
        if (reason.isBlank() || reason.length > MAX_TEXT) throw DomainException(ErrorCode.RETURN_INVALID)
        status = ReturnStatus.REJECTED
        rejectReason = reason
        decidedAt = now
        decidedBy = DecidedBy.SELLER
    }

    fun withdraw() {
        if (status == ReturnStatus.WITHDRAWN) return
        if (status != ReturnStatus.REQUESTED) throw invalidTransition()
        status = ReturnStatus.WITHDRAWN
    }

    fun cancelByAdmin() {
        if (status != ReturnStatus.APPROVED) throw invalidTransition()
        status = ReturnStatus.CANCELED_BY_ADMIN
    }

    fun startRefund(plan: ReturnRefundPlan, now: LocalDateTime) {
        if (status != ReturnStatus.APPROVED) throw invalidTransition()
        returnFee = plan.returnFee
        outboundFeeRecovered = plan.outboundFeeRecovered
        shippingFeeRefund = plan.shippingFeeRefund
        sellerBorneFee = plan.sellerBorneFee
        refundAmount = plan.refundAmount
        pointsRefund = plan.pointsRefund
        receivedAt = now
        status = ReturnStatus.REFUNDING
    }

    /** PG 실패로 되돌린다. 계산해 둔 금액을 모두 지워 다음 계산이 이 반품을 이미 회수한 것으로 보지 않게 한다. */
    fun revertRefund() {
        if (status != ReturnStatus.REFUNDING) throw invalidTransition()
        clearAmounts()
        receivedAt = null
        status = ReturnStatus.APPROVED
    }

    fun complete(now: LocalDateTime) {
        if (status != ReturnStatus.REFUNDING) throw invalidTransition()
        status = ReturnStatus.COMPLETED
        completedAt = now
    }

    /** 수거 확인 때 저장해 둔 값에서 환불 계획을 복원한다(PG 취소 후 확정 단계에서 쓴다). */
    fun toRefundPlan(paidAmount: Long, productCouponReturn: Long?): ReturnRefundPlan = ReturnRefundPlan(
        itemId = orderItemId,
        paidAmount = paidAmount,
        returnFee = returnFee,
        outboundFeeRecovered = outboundFeeRecovered,
        shippingFeeRefund = shippingFeeRefund,
        pointsRefund = pointsRefund,
        productCouponReturn = productCouponReturn,
        sellerBorneFee = sellerBorneFee,
    )

    private fun clearAmounts() {
        returnFee = 0
        outboundFeeRecovered = 0
        shippingFeeRefund = 0
        sellerBorneFee = 0
        refundAmount = 0
        pointsRefund = 0
    }

    private fun invalidTransition() = DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)

    companion object {
        private const val MAX_TEXT = 500

        private fun validateDetail(detail: String?) {
            if (detail != null && detail.length > MAX_TEXT) throw DomainException(ErrorCode.RETURN_INVALID)
        }

        fun request(
            orderId: Long,
            sellerOrderId: Long,
            orderItemId: Long,
            memberId: Long,
            sellerId: Long,
            reason: ReturnReason,
            detail: String?,
            now: LocalDateTime,
        ): ReturnRequest {
            validateDetail(detail)
            return ReturnRequest(
                id = null, orderId = orderId, sellerOrderId = sellerOrderId, orderItemId = orderItemId,
                memberId = memberId, sellerId = sellerId, reason = reason, detail = detail,
                status = ReturnStatus.REQUESTED, returnFee = 0, outboundFeeRecovered = 0, shippingFeeRefund = 0,
                sellerBorneFee = 0, refundAmount = 0, pointsRefund = 0, rejectReason = null, requestedAt = now,
                decidedAt = null, decidedBy = null, receivedAt = null, completedAt = null,
            )
        }
    }
}
