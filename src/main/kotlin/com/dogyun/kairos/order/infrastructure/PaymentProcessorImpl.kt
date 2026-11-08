package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.PaymentProcessor
import com.dogyun.kairos.order.application.PaymentResult
import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.payment.application.FailReason
import com.dogyun.kairos.payment.application.PayResult
import com.dogyun.kairos.payment.application.PaymentService
import org.springframework.stereotype.Component

/** payment 모듈의 application 서비스를 호출하고 결과를 order 측 타입으로 바꾼다. */
@Component
class PaymentProcessorImpl(private val paymentService: PaymentService) : PaymentProcessor {

    override fun pay(orderId: Long, amount: Long): PaymentResult =
        when (val result = paymentService.pay(orderId, amount)) {
            is PayResult.Approved -> PaymentResult.Approved(result.paymentId)
            is PayResult.Failed -> PaymentResult.Failed(toCancelReason(result.reason))
        }

    private fun toCancelReason(reason: FailReason) = when (reason) {
        FailReason.PAYMENT_FAILED -> CancelReason.PAYMENT_FAILED
        FailReason.PAYMENT_TIMEOUT -> CancelReason.PAYMENT_TIMEOUT
    }
}
