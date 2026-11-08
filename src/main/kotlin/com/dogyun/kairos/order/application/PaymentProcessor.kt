package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.CancelReason

/** payment 모듈 호출용 인터페이스. 결제 실패는 예외가 아니라 결과로 돌려준다. */
interface PaymentProcessor {
    fun pay(orderId: Long, amount: Long): PaymentResult

    /** 승인된 결제를 환불한다. 환불할 수 없으면 예외(호출 측 오류). */
    fun refund(orderId: Long)
}

/** order 가 바라보는 결제 결과. payment 모듈 타입을 노출하지 않는다. */
sealed interface PaymentResult {
    data class Approved(val paymentId: Long) : PaymentResult
    data class Failed(val reason: CancelReason) : PaymentResult
}
