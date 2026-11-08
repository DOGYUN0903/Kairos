package com.dogyun.kairos.payment.application

/** 결제(PG 호출) 메트릭. 구현(Micrometer)은 payment.infrastructure 에 둔다. */
interface PaymentMetrics {
    /** result = APPROVED | FAILED | TIMEOUT (PgResponse 이름 그대로). */
    fun recordDuration(result: String, nanos: Long)
}
