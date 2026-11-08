package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(private val jpaRepository: PaymentJpaRepository) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = payment.id
            ?.let { jpaRepository.findById(it).orElseThrow() }
            ?.also { it.change(payment.status) }
            ?: PaymentEntity(payment.orderId, payment.amount, payment.status, payment.failReason)
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findLatestByOrderId(orderId: Long): Payment? =
        jpaRepository.findFirstByOrderIdOrderByIdDesc(orderId)?.let(::toDomain)

    private fun toDomain(e: PaymentEntity) =
        Payment.restore(e.id, e.orderId, e.amount, e.paymentStatus, e.failReason)
}
