package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

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
                log.error("결제 승인 대사에 실패했다: orderId={}", payment.orderId, e)
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
     * 닫힌(만료·승인 실패로 CANCELED 된, 결제 완료된 적 없는) 주문의 결제가 PG 에 남아 있으면 같은 멱등 키로 다시 전액 취소한다.
     * void 호출이 유실되거나(결제 `UNKNOWN`) 금액 불일치 취소가 확인되지 않은 채 거절된(결제 `FAILED`) 경우를 로컬 결제 상태와 무관하게 본다.
     * 다시 취소한 주문 수.
     */
    fun revoidClosedOrders(limit: Int = 100): Int {
        val since = LocalDateTime.now(clock).minus(REVOID_WINDOW)
        return orderRepository.findCanceledOrderIdsPaidWithin(since, limit).count { orderId ->
            try {
                revoid(orderId)
            } catch (e: RuntimeException) {
                log.error("닫힌 주문 재취소에 실패했다: orderId={}", orderId, e)
                false
            }
        }
    }

    private fun revoid(orderId: Long): Boolean {
        val payment = paymentGateway.get(orderId)
        val paymentKey = payment.paymentKey ?: return false
        val pg = (paymentGateway.findFromPg(paymentKey) as? PgOutcome.Done)?.payment ?: return false
        if (pg.status != PgPaymentStatus.DONE && pg.status != PgPaymentStatus.PARTIAL_CANCELED) return false
        if (pg.balanceAmount <= 0) return false

        return orderPaymentService.voidClosedOrder(orderId, paymentKey, pg.balanceAmount)
    }

    /** `CANCEL_REQUESTED` 로 정체된 항목을 PG 조회로 확정한다. 환불을 확정(CANCELED)한 항목 수. */
    fun reconcileCancels(limit: Int = 100): Int {
        val items = orderRepository.findCancelRequestedItems(LocalDateTime.now(clock).minus(properties.cancelRequestTimeout), limit)
        return items.count { (orderId, itemId) ->
            try {
                reconcileCancel(orderId, itemId)
            } catch (e: RuntimeException) {
                log.error("환불 대사에 실패했다: orderId={}, itemId={}", orderId, itemId, e)
                false
            }
        }
    }

    private fun reconcileCancel(orderId: Long, itemId: Long): Boolean {
        val item = orderRepository.findById(orderId)?.findItem(itemId) ?: return false
        // 환불 0원이면 PG 를 볼 필요가 없다(finishCancel 이 환불 0 을 먼저 확정한다).
        val outcome = if (item.refundAmount == 0L) NO_REFUND else refundOutcome(orderId, itemId, item.refundAmount) ?: return false
        // REVERTED(항목이 이미 ORDERED 로 돌아가 있음)와 PENDING 은 할 일이 없다.
        return orderCancelService.finishCancel(orderId, itemId, outcome) == CancelFinish.CANCELED
    }

    /** PG 에 이 항목의 취소가 있으면 그 결과를, 없으면 같은 멱등 키로 다시 취소해 얻은 결과를 돌려준다. 확인할 수 없으면 null. */
    private fun refundOutcome(orderId: Long, itemId: Long, refundAmount: Long): PgOutcome? {
        val paymentKey = paymentGateway.get(orderId).paymentKey ?: return null
        val found = paymentGateway.findFromPg(paymentKey)
        // 조회가 일시적으로 실패한 것일 수 있다(NOT_FOUND 만 PG 에 취소가 없다는 확실한 답이다). 취소를 다시 부르다 거절되면 환불이 되돌려지므로 이번 주기는 건너뛴다.
        if (found is PgOutcome.Unknown || (found is PgOutcome.Failed && found.code != "NOT_FOUND")) return null
        if (found is PgOutcome.Done && found.payment.cancels.any { it.reason == "orderItem:$itemId" }) return found
        return paymentGateway.cancelWithPg(paymentKey, refundAmount, "orderItem:$itemId", "refund-$itemId")
    }

    private companion object {
        val log = LoggerFactory.getLogger(PaymentReconciliationService::class.java)
        const val RECONCILED = "RECONCILED"
        val REVOID_WINDOW: Duration = Duration.ofHours(24)
        val NO_REFUND: PgOutcome = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 0, 0, emptyList()))
    }
}
