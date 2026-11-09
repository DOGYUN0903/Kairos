package com.dogyun.kairos.payment.domain

import java.time.LocalDateTime

interface PaymentRepository {
    /** payment_key UNIQUE 위반은 [com.dogyun.kairos.common.domain.DomainException](PAYMENT_KEY_DUPLICATED)로 바뀐다. */
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: Long): Payment?
    fun findByPaymentKey(key: String): Payment?
    fun findStaleInProgress(before: LocalDateTime, limit: Int): List<Payment>
    fun findAllByStatus(status: PaymentStatus, limit: Int): List<Payment>
}
