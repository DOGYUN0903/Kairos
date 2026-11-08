package com.dogyun.kairos.payment.application

import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 주문 트랜잭션 안에서 동기 호출된다(참여). PG 대기 동안 호출 스레드는 블로킹된다. */
@Service
class PaymentService(
    private val pgClient: PgClient,
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun pay(orderId: Long, amount: Long): PayResult {
        val reason = when (pgClient.approve(orderId, amount)) {
            PgResponse.APPROVED -> null
            PgResponse.FAILED -> FailReason.PAYMENT_FAILED
            PgResponse.TIMEOUT -> FailReason.PAYMENT_TIMEOUT
        }
        if (reason == null) {
            val saved = paymentRepository.save(Payment.approved(orderId, amount))
            return PayResult.Approved(checkNotNull(saved.id))
        }
        paymentRepository.save(Payment.failed(orderId, amount, reason.name))
        return PayResult.Failed(reason)
    }

    /** 승인된 결제가 없으면 IllegalStateException(호출 측 오류). */
    @Transactional
    fun refund(orderId: Long) {
        val payment = checkNotNull(paymentRepository.findLatestByOrderId(orderId)) { "결제 내역이 없습니다: orderId=$orderId" }
        payment.refund()
        paymentRepository.save(payment)
    }
}
