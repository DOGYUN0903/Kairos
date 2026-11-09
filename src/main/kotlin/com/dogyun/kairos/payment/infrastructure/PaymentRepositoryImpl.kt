package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import com.dogyun.kairos.payment.domain.PaymentStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PaymentRepositoryImpl(private val jpaRepository: PaymentJpaRepository) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val id = payment.id
        val entity = if (id == null) {
            PaymentEntity(
                payment.orderId, payment.orderNo, payment.amount, payment.paymentKey, payment.refundedAmount,
                payment.status, payment.failReason, payment.inProgressAt, payment.approvedAt,
            )
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id)) { "존재하지 않는 결제입니다: $id" }.also {
                it.change(
                    payment.paymentKey, payment.refundedAmount, payment.status, payment.failReason,
                    payment.inProgressAt, payment.approvedAt,
                )
            }
        }
        return try {
            toDomain(jpaRepository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            // payment_key UNIQUE 가 같은 키의 중복 승인을 마지막으로 막는다.
            throw DomainException(ErrorCode.PAYMENT_KEY_DUPLICATED)
        }
    }

    override fun findByOrderId(orderId: Long): Payment? = jpaRepository.findByOrderId(orderId)?.let(::toDomain)

    override fun findByPaymentKey(key: String): Payment? = jpaRepository.findByPaymentKey(key)?.let(::toDomain)

    override fun findStaleInProgress(before: LocalDateTime, limit: Int): List<Payment> =
        jpaRepository.findByPaymentStatusAndInProgressAtBeforeOrderById(PaymentStatus.IN_PROGRESS, before, PageRequest.ofSize(limit))
            .map(::toDomain)

    override fun findAllByStatus(status: PaymentStatus, limit: Int): List<Payment> =
        jpaRepository.findByPaymentStatusOrderById(status, PageRequest.ofSize(limit)).map(::toDomain)

    private fun toDomain(e: PaymentEntity) = Payment(
        e.id, e.orderId, e.orderNo, e.paymentKey, e.amount, e.refundedAmount, e.paymentStatus, e.failReason,
        e.inProgressAt, e.approvedAt,
    )
}
