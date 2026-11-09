package com.dogyun.kairos.payment.application

enum class PgStatus { DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED, OTHER }

data class PgCancel(val amount: Long, val reason: String)

data class PgPayment(
    val paymentKey: String,
    val pgOrderId: String,
    val status: PgStatus,
    val totalAmount: Long,
    val balanceAmount: Long,
    val cancels: List<PgCancel>,
)

sealed interface PgResult {
    data class Done(val payment: PgPayment) : PgResult
    data class Failed(val code: String, val message: String) : PgResult

    /** 타임아웃 등으로 PG 처리 결과를 알 수 없다. [PgClient.find]로 확인해야 한다. */
    data class Unknown(val cause: String) : PgResult
}

interface PgClient {
    fun confirm(paymentKey: String, pgOrderId: String, amount: Long): PgResult
    fun cancel(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgResult

    /** 없는 결제는 `Failed("NOT_FOUND", …)`. */
    fun find(paymentKey: String): PgResult
}
