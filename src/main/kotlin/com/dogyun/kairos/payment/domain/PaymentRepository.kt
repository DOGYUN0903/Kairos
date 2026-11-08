package com.dogyun.kairos.payment.domain

interface PaymentRepository {
    fun save(payment: Payment): Payment

    /** 같은 주문의 가장 최근 결제 시도. */
    fun findLatestByOrderId(orderId: Long): Payment?
}
