package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.domain.PaymentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentEntity?
    fun findByPaymentKey(paymentKey: String): PaymentEntity?
    fun findByPaymentStatusAndInProgressAtBeforeOrderById(
        status: PaymentStatus,
        before: LocalDateTime,
        pageable: Pageable,
    ): List<PaymentEntity>

    fun findByPaymentKeyIsNotNullAndPaymentStatusInAndIdGreaterThanOrderById(
        statuses: Collection<PaymentStatus>,
        afterId: Long,
        pageable: Pageable,
    ): List<PaymentEntity>

    fun findByPaymentStatusOrderById(status: PaymentStatus, pageable: Pageable): List<PaymentEntity>
}
