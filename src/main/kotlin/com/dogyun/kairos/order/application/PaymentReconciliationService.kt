package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.sweepByKeyset
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

/**
 * PG 를 다시 조회해 결제 승인·환불의 불확실한 상태를 확정하는 정리 작업. PG 호출은 트랜잭션 밖이고, 상태 변경은
 * [OrderPaymentService.finishApproval] / [OrderCancelService.finishCancel] 의 자체 트랜잭션(주문 행 락 먼저)으로만 한다.
 * 이 클래스에는 `@Transactional` 을 붙이지 않는다.
 */
@Service
class PaymentReconciliationService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val orderPaymentService: OrderPaymentService,
    private val orderCancelService: OrderCancelService,
    private val orderReturnService: OrderReturnService,
    private val properties: OrderProperties,
    private val clock: Clock,
) {

    /** 정체된 `IN_PROGRESS` 와 `UNKNOWN` 결제를 PG 결과로 확정한다. 확정한(건너뛰지 않은) 결제 수. */
    fun reconcileApprovals(limit: Int = 100): Int {
        val stale = paymentGateway.findStale(LocalDateTime.now(clock).minus(properties.inProgressTimeout), limit)
        return stale.count { payment ->
            try {
                reconcileApproval(payment)
            } catch (e: RuntimeException) {
                log.error(e) { "결제 승인 대사에 실패했다: orderId=${payment.orderId}" }
                false
            }
        }
    }

    private fun reconcileApproval(payment: PaymentInfo): Boolean {
        val paymentKey = payment.paymentKey ?: return false
        val orderId = payment.orderId
        val outcome = paymentGateway.findFromPg(paymentKey)
        var verdict = judge(outcome, payment.amount)
        if (verdict == PgVerdict.VOID_THEN_REJECT) {
            verdict = orderPaymentService.voidMismatch(orderId, paymentKey, (outcome as PgOutcome.Done).payment.totalAmount)
        }
        if (verdict == PgVerdict.HOLD) return false

        val finish = orderPaymentService.finishApproval(orderId, paymentKey, verdict, RECONCILED)
        if (finish == ApprovalFinish.ORDER_ALREADY_CLOSED && verdict == PgVerdict.APPROVE) {
            orderPaymentService.voidClosedOrder(orderId, paymentKey, payment.amount)
        }
        return finish != ApprovalFinish.ALREADY_SETTLED
    }

    /**
     * 닫힌(CANCELED) 주문의 결제가 PG 에 남아 있으면 전액 다시 취소한다. 후보는 결제 쪽에서 고른다:
     * `payment_key` 가 있고 `FAILED`/`UNKNOWN` 인 결제(void 호출이 유실됐거나 금액 불일치 취소가 확인되지 않은 채 거절된 경우)를
     * 결제 id 순 keyset 으로 훑고, 그중 주문이 `CANCELED` 인 것만 본다. PG 가 남은 잔액이 없다고 확정해 답하면 결제를 닫아
     * 더는 후보가 되지 않는다. 조회를 확정하지 못하면(일시 오류, 알 수 없는 상태) 후보로 남는다. 다시 취소한 주문 수.
     */
    fun revoidClosedOrders(limit: Int = 100): Int =
        sweepByKeyset(limit, { it.paymentId }, { after -> paymentGateway.findVoidCandidates(after, limit) }) { candidate ->
            try {
                revoid(candidate)
            } catch (e: RuntimeException) {
                log.error(e) { "닫힌 주문 재취소에 실패했다: orderId=${candidate.orderId}" }
                false
            }
        }

    private fun revoid(candidate: PaymentInfo): Boolean {
        val orderId = candidate.orderId
        val paymentKey = candidate.paymentKey ?: return false
        if (orderId !in orderRepository.findCanceledIdsAmong(listOf(orderId))) return false

        val outcome = paymentGateway.findFromPg(paymentKey)
        val pg = when (outcome) {
            is PgOutcome.Done -> outcome.payment
            is PgOutcome.Failed -> {
                // NOT_FOUND 만 PG 에 결제가 없다는 확실한 답이다. 그 밖의 실패는 일시 오류일 수 있어 다음 주기에 다시 본다.
                if (outcome.code == "NOT_FOUND") orderPaymentService.closeVoidedPayment(orderId, paymentKey)
                return false
            }
            is PgOutcome.Unknown -> return false
        }
        when (pg.status) {
            PgPaymentStatus.DONE, PgPaymentStatus.PARTIAL_CANCELED -> Unit
            PgPaymentStatus.CANCELED, PgPaymentStatus.ABORTED, PgPaymentStatus.EXPIRED -> {
                orderPaymentService.closeVoidedPayment(orderId, paymentKey)
                return false
            }
            PgPaymentStatus.OTHER -> return false
        }
        if (pg.balanceAmount <= 0) {
            orderPaymentService.closeVoidedPayment(orderId, paymentKey)
            return false
        }

        return orderPaymentService.voidClosedOrder(orderId, paymentKey, pg.balanceAmount)
    }

    /**
     * `CANCEL_REQUESTED`·`RETURN_REFUNDING` 으로 정체된 항목을 PG 조회로 확정한다. 환불을 확정(CANCELED·RETURNED)한 항목 수.
     * [limit] 은 keyset 페이지 크기다.
     */
    fun reconcileCancels(limit: Int = 100): Int {
        val before = LocalDateTime.now(clock).minus(properties.cancelRequestTimeout)
        return sweepByKeyset(limit, { it.second }, { after -> orderRepository.findItemsInStatusBefore(setOf(OrderItemStatus.CANCEL_REQUESTED, OrderItemStatus.RETURN_REFUNDING), before, after, limit) }) { (orderId, itemId) ->
            try {
                reconcileCancel(orderId, itemId)
            } catch (e: RuntimeException) {
                log.error(e) { "환불 대사에 실패했다: orderId=$orderId, itemId=$itemId" }
                false
            }
        }
    }

    private fun reconcileCancel(orderId: Long, itemId: Long): Boolean {
        val item = orderRepository.findById(orderId)?.findItem(itemId) ?: return false
        if (item.status == OrderItemStatus.RETURN_REFUNDING) {
            val request = item.returnRequest ?: return false
            val returnId = request.id ?: return false
            val outcome = if (request.refundAmount == 0L) NO_REFUND else refundOutcome(orderId, itemId, request.refundAmount) ?: return false
            return orderReturnService.finishReturn(orderId, returnId, outcome) == ReturnFinish.RETURNED
        }
        // 환불 0원이면 PG 를 볼 필요가 없다(finishCancel 이 환불 0 을 먼저 확정한다).
        val outcome = if (item.refundAmount == 0L) NO_REFUND else refundOutcome(orderId, itemId, item.refundAmount) ?: return false
        // REVERTED(항목이 이미 ORDERED 로 돌아가 있음)와 PENDING 은 할 일이 없다.
        return orderCancelService.finishCancel(orderId, itemId, outcome) == CancelFinish.CANCELED
    }

    /** PG 에 이 항목의 취소가 있으면 그 결과를, 없으면 같은 멱등 키로 다시 취소해 얻은 결과를 돌려준다. 확인할 수 없으면 null. */
    private fun refundOutcome(orderId: Long, itemId: Long, refundAmount: Long): PgOutcome? {
        val paymentKey = paymentGateway.get(orderId).paymentKey
        if (paymentKey == null) {
            // 현재 도달 불가: paymentKey 없는 주문(PG 미사용)은 현금 환불이 0원이라 여기까지 오지 않는다.
            // 무PG 결제수단을 추가하면 이 항목은 영원히 건너뛰어지므로 관리자 종결 경로가 필요하다. 그때까지 매 주기 WARN 으로 드러낸다.
            log.error { "PG 결제 키가 없는 환불 대기 항목을 건너뛴다(수동 확인 필요): orderId=$orderId, itemId=$itemId, refundAmount=$refundAmount" }
            return null
        }
        val found = paymentGateway.findFromPg(paymentKey)
        // 조회가 일시적으로 실패한 것일 수 있다(NOT_FOUND 만 PG 에 취소가 없다는 확실한 답이다). 취소를 다시 부르다 거절되면 환불이 되돌려지므로 이번 주기는 건너뛴다.
        if (found is PgOutcome.Unknown || (found is PgOutcome.Failed && found.code != "NOT_FOUND")) return null
        if (found is PgOutcome.Done && found.payment.cancels.any { it.reason == "orderItem:$itemId" }) return found
        return paymentGateway.cancelWithPg(paymentKey, refundAmount, "orderItem:$itemId", refundKey(itemId, refundAmount))
    }

    private companion object {
        const val RECONCILED = "RECONCILED"
        val NO_REFUND: PgOutcome = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 0, 0, emptyList()))
    }
}
