package com.dogyun.kairos.payment.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import com.dogyun.kairos.payment.domain.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val clock: Clock,
) {

    @Transactional
    fun prepare(orderId: Long, orderNo: String, amount: Long): Long =
        requireNotNull(paymentRepository.save(Payment.ready(orderId, orderNo, amount)).id)

    @Transactional
    fun start(orderId: Long, paymentKey: String): PaymentSnapshot {
        val other = paymentRepository.findByPaymentKey(paymentKey)
        if (other != null && other.orderId != orderId) throw DomainException(ErrorCode.PAYMENT_KEY_DUPLICATED)
        return change(orderId) { it.start(paymentKey, LocalDateTime.now(clock)) }
    }

    @Transactional
    fun approve(orderId: Long): PaymentSnapshot = change(orderId) { it.approve(LocalDateTime.now(clock)) }

    @Transactional
    fun fail(orderId: Long, reason: String): PaymentSnapshot = change(orderId) { it.fail(reason) }

    @Transactional
    fun markUnknown(orderId: Long): PaymentSnapshot = change(orderId) { it.markUnknown() }

    @Transactional
    fun voidAfterApproval(orderId: Long): PaymentSnapshot = change(orderId) { it.voidAfterApproval() }

    @Transactional
    fun recordRefund(orderId: Long, amount: Long): PaymentSnapshot = change(orderId) { it.refund(amount) }

    @Transactional
    fun get(orderId: Long): PaymentSnapshot = load(orderId).toSnapshot()

    /** `IN_PROGRESS` 로 [inProgressBefore] 이전부터 정체된 결제와 `UNKNOWN` 전부. */
    @Transactional
    fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentSnapshot> =
        (
            paymentRepository.findStaleInProgress(inProgressBefore, limit) +
                paymentRepository.findAllByStatus(PaymentStatus.UNKNOWN, limit)
            ).take(limit).map { it.toSnapshot() }

    private fun load(orderId: Long): Payment =
        paymentRepository.findByOrderId(orderId) ?: throw DomainException(ErrorCode.PAYMENT_NOT_FOUND)

    private fun change(orderId: Long, action: (Payment) -> Unit): PaymentSnapshot {
        val payment = load(orderId)
        action(payment)
        return paymentRepository.save(payment).toSnapshot()
    }

    private fun Payment.toSnapshot() =
        PaymentSnapshot(requireNotNull(id), orderId, orderNo, paymentKey, amount, refundedAmount, status.name, failReason)
}
