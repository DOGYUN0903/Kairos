package com.dogyun.kairos.payment.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

class Payment(
    val id: Long?,
    val orderId: Long,
    val orderNo: String,
    paymentKey: String?,
    val amount: Long,
    refundedAmount: Long,
    status: PaymentStatus,
    failReason: String?,
    inProgressAt: LocalDateTime?,
    approvedAt: LocalDateTime?,
) {
    var paymentKey: String? = paymentKey
        private set
    var refundedAmount: Long = refundedAmount
        private set
    var status: PaymentStatus = status
        private set
    var failReason: String? = failReason
        private set
    var inProgressAt: LocalDateTime? = inProgressAt
        private set
    var approvedAt: LocalDateTime? = approvedAt
        private set

    val balance: Long get() = amount - refundedAmount

    /** PG 승인 시도를 시작한다. 실패한 결제를 다시 시작하지 않는다(재시도는 새 주문). */
    fun start(paymentKey: String, now: LocalDateTime) {
        ensure(status == PaymentStatus.READY)
        this.paymentKey = paymentKey
        this.inProgressAt = now
        status = PaymentStatus.IN_PROGRESS
    }

    fun approve(now: LocalDateTime) {
        ensure(status == PaymentStatus.IN_PROGRESS || status == PaymentStatus.UNKNOWN)
        approvedAt = now
        status = PaymentStatus.APPROVED
    }

    fun fail(reason: String) {
        ensure(status == PaymentStatus.READY || status == PaymentStatus.IN_PROGRESS || status == PaymentStatus.UNKNOWN)
        failReason = reason.take(MAX_FAIL_REASON)
        status = PaymentStatus.FAILED
    }

    fun markUnknown() {
        ensure(status == PaymentStatus.IN_PROGRESS)
        status = PaymentStatus.UNKNOWN
    }

    /** 주문이 이미 닫힌 뒤 PG 승인이 확인된 경우: 전액을 PG 에서 취소했다고 기록한다. */
    fun voidAfterApproval() {
        ensure(status == PaymentStatus.IN_PROGRESS || status == PaymentStatus.UNKNOWN)
        refundedAmount = amount
        status = PaymentStatus.CANCELED
    }

    /**
     * 닫힌 주문의 결제에 PG 잔액이 남아 있지 않음이 PG 조회로 확인됐다(취소가 확인됐거나 처음부터 승인되지 않았다).
     * 결제를 `CANCELED` 로 닫아 더는 재취소 후보가 되지 않게 한다. 환불액은 바꾸지 않는다.
     */
    fun markVoided() {
        ensure(paymentKey != null && (status == PaymentStatus.FAILED || status == PaymentStatus.UNKNOWN))
        status = PaymentStatus.CANCELED
    }

    fun refund(amount: Long) {
        ensure(status == PaymentStatus.APPROVED || status == PaymentStatus.PARTIAL_CANCELED)
        ensure(amount > 0 && refundedAmount + amount <= this.amount)
        refundedAmount += amount
        status = if (refundedAmount == this.amount) PaymentStatus.CANCELED else PaymentStatus.PARTIAL_CANCELED
    }

    private fun ensure(condition: Boolean) {
        if (!condition) throw DomainException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION)
    }

    companion object {
        private const val MAX_FAIL_REASON = 100

        fun ready(orderId: Long, orderNo: String, amount: Long) =
            Payment(null, orderId, orderNo, null, amount, 0L, PaymentStatus.READY, null, null, null)
    }
}
