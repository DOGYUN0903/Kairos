package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.PaymentGateway
import com.dogyun.kairos.order.application.PaymentInfo
import com.dogyun.kairos.order.application.PgCancelInfo
import com.dogyun.kairos.order.application.PgOutcome
import com.dogyun.kairos.order.application.PgPaymentInfo
import com.dogyun.kairos.order.application.PgPaymentStatus
import com.dogyun.kairos.payment.application.PaymentService
import com.dogyun.kairos.payment.application.PaymentSnapshot
import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgPayment
import com.dogyun.kairos.payment.application.PgResult
import com.dogyun.kairos.payment.application.PgStatus
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** order 의 [PaymentGateway] 를 payment 의 [PaymentService]·[PgClient] 로 구현하고 타입을 번역한다. PG 호출은 트랜잭션 밖이다. */
@Component
class PaymentGatewayImpl(
    private val paymentService: PaymentService,
    private val pgClient: PgClient,
) : PaymentGateway {

    override fun prepare(orderId: Long, orderNo: String, amount: Long) {
        paymentService.prepare(orderId, orderNo, amount)
    }

    override fun start(orderId: Long, paymentKey: String): PaymentInfo = paymentService.start(orderId, paymentKey).toInfo()

    override fun approve(orderId: Long) {
        paymentService.approve(orderId)
    }

    override fun fail(orderId: Long, reason: String) {
        paymentService.fail(orderId, reason)
    }

    override fun markUnknown(orderId: Long) {
        paymentService.markUnknown(orderId)
    }

    override fun voidAfterApproval(orderId: Long) {
        paymentService.voidAfterApproval(orderId)
    }

    override fun recordRefund(orderId: Long, amount: Long) {
        paymentService.recordRefund(orderId, amount)
    }

    override fun get(orderId: Long): PaymentInfo = paymentService.get(orderId).toInfo()

    override fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo> =
        paymentService.findStale(inProgressBefore, limit).map { it.toInfo() }

    override fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome =
        pgClient.confirm(paymentKey, orderNo, amount).toOutcome()

    override fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome =
        pgClient.cancel(paymentKey, amount, reason, idempotencyKey).toOutcome()

    override fun findFromPg(paymentKey: String): PgOutcome = pgClient.find(paymentKey).toOutcome()

    private fun PaymentSnapshot.toInfo() =
        PaymentInfo(paymentId, orderId, orderNo, paymentKey, amount, refundedAmount, status, failReason)

    private fun PgResult.toOutcome(): PgOutcome = when (this) {
        is PgResult.Done -> PgOutcome.Done(payment.toInfo())
        is PgResult.Failed -> PgOutcome.Failed(code, message)
        is PgResult.Unknown -> PgOutcome.Unknown
    }

    private fun PgPayment.toInfo() = PgPaymentInfo(
        status = when (status) {
            PgStatus.DONE -> PgPaymentStatus.DONE
            PgStatus.CANCELED -> PgPaymentStatus.CANCELED
            PgStatus.PARTIAL_CANCELED -> PgPaymentStatus.PARTIAL_CANCELED
            PgStatus.ABORTED -> PgPaymentStatus.ABORTED
            PgStatus.EXPIRED -> PgPaymentStatus.EXPIRED
            PgStatus.OTHER -> PgPaymentStatus.OTHER
        },
        totalAmount = totalAmount,
        balanceAmount = balanceAmount,
        cancels = cancels.map { PgCancelInfo(it.amount, it.reason) },
    )
}
