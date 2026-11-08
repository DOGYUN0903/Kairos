package com.dogyun.kairos.payment.domain

class Payment private constructor(
    val id: Long?,
    val orderId: Long,
    val amount: Long,
    status: PaymentStatus,
    val failReason: String?,
) {
    var status: PaymentStatus = status
        private set

    /** 환불은 승인된 결제에만 가능하다. 그 외는 호출 측 오류이므로 IllegalStateException. */
    fun refund() {
        check(status == PaymentStatus.APPROVED) { "승인된 결제만 환불할 수 있습니다: orderId=$orderId, status=$status" }
        status = PaymentStatus.REFUNDED
    }

    companion object {
        fun approved(orderId: Long, amount: Long) = Payment(null, orderId, amount, PaymentStatus.APPROVED, null)

        fun failed(orderId: Long, amount: Long, reason: String) =
            Payment(null, orderId, amount, PaymentStatus.FAILED, reason)

        fun restore(id: Long, orderId: Long, amount: Long, status: PaymentStatus, failReason: String?) =
            Payment(id, orderId, amount, status, failReason)
    }
}
