package com.dogyun.kairos.payment.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import com.dogyun.kairos.payment.domain.PaymentStatus
import java.time.LocalDateTime

class FakePaymentRepository : PaymentRepository {

    private val rows = LinkedHashMap<Long, Payment>()
    private var seq = 0L

    override fun save(payment: Payment): Payment {
        val key = payment.paymentKey
        if (key != null && rows.values.any { it.paymentKey == key && it.id != payment.id }) {
            throw DomainException(ErrorCode.PAYMENT_KEY_DUPLICATED)
        }
        val id = payment.id ?: ++seq
        val saved = Payment(
            id, payment.orderId, payment.orderNo, payment.paymentKey, payment.amount, payment.refundedAmount,
            payment.status, payment.failReason, payment.inProgressAt, payment.approvedAt,
        )
        rows[id] = saved
        return copyOf(saved)
    }

    override fun findByOrderId(orderId: Long): Payment? = rows.values.firstOrNull { it.orderId == orderId }?.let(::copyOf)

    override fun findByPaymentKey(key: String): Payment? = rows.values.firstOrNull { it.paymentKey == key }?.let(::copyOf)

    override fun findStaleInProgress(before: LocalDateTime, limit: Int): List<Payment> =
        rows.values.filter { it.status == PaymentStatus.IN_PROGRESS && it.inProgressAt!! < before }.take(limit).map(::copyOf)

    override fun findAllByStatus(status: PaymentStatus, limit: Int): List<Payment> =
        rows.values.filter { it.status == status }.take(limit).map(::copyOf)

    private fun copyOf(p: Payment) = Payment(
        p.id, p.orderId, p.orderNo, p.paymentKey, p.amount, p.refundedAmount, p.status, p.failReason, p.inProgressAt, p.approvedAt,
    )
}
