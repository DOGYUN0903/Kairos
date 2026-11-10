package com.dogyun.kairos.order.application

import java.time.LocalDateTime

enum class PgPaymentStatus { DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED, OTHER }

data class PgCancelInfo(val amount: Long, val reason: String)

data class PgPaymentInfo(
    val status: PgPaymentStatus,
    val totalAmount: Long,
    val balanceAmount: Long,
    val cancels: List<PgCancelInfo>,
)

sealed interface PgOutcome {
    data class Done(val payment: PgPaymentInfo) : PgOutcome
    data class Failed(val code: String, val message: String) : PgOutcome

    /** PG 처리 결과를 알 수 없다(타임아웃 등). */
    data object Unknown : PgOutcome
}

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val orderNo: String,
    val paymentKey: String?,
    val amount: Long,
    val refundedAmount: Long,
    /** READY, IN_PROGRESS, APPROVED, PARTIAL_CANCELED, CANCELED, UNKNOWN, FAILED */
    val status: String,
    val failReason: String?,
)

/**
 * order 가 payment 를 보는 창구. 구현은 order.infrastructure 에서 payment 의 PaymentService·PgClient 로 하고 타입을 번역한다.
 * `*WithPg`, `findFromPg` 는 PG 를 호출하므로 트랜잭션을 열지 않는다.
 */
interface PaymentGateway {
    fun prepare(orderId: Long, orderNo: String, amount: Long)
    fun start(orderId: Long, paymentKey: String): PaymentInfo
    fun approve(orderId: Long)

    /** 0원 결제(`READY`, 금액 0)를 PG 없이 `APPROVED` 로 만든다. */
    fun approveWithoutPg(orderId: Long)
    fun fail(orderId: Long, reason: String)
    fun markUnknown(orderId: Long)
    fun voidAfterApproval(orderId: Long)
    fun recordRefund(orderId: Long, amount: Long)
    fun get(orderId: Long): PaymentInfo
    fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo>

    /** 재취소 후보 결제(`payment_key` 있음, `FAILED`/`UNKNOWN`)를 결제 id 오름차순 keyset 으로 가져온다. 주문 상태는 order 쪽에서 거른다. */
    fun findVoidCandidates(afterPaymentId: Long, limit: Int): List<PaymentInfo>

    /** PG 잔액이 없음이 확인된 `FAILED`/`UNKNOWN` 결제를 닫아 더는 후보가 되지 않게 한다. */
    fun markVoided(orderId: Long)
    fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome
    fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome
    fun findFromPg(paymentKey: String): PgOutcome
}
