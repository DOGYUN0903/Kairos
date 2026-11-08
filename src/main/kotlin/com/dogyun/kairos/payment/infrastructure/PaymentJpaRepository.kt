package com.dogyun.kairos.payment.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentEntity?
}
