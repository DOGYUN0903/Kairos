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
    fun fail(orderId: Long, reason: String)
    fun markUnknown(orderId: Long)
    fun voidAfterApproval(orderId: Long)
    fun recordRefund(orderId: Long, amount: Long)
    fun get(orderId: Long): PaymentInfo
    fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo>
    fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome
    fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome
    fun findFromPg(paymentKey: String): PgOutcome
}
