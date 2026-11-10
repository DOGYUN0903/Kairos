package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/** PG 결과를 받은 뒤 결제·주문을 어떻게 마무리할지. 승인(confirm)과 대사(PG 조회)가 같은 판단을 쓴다. */
enum class PgVerdict { APPROVE, REJECT, VOID_THEN_REJECT, HOLD }

enum class ApprovalFinish {
    PAID,
    REJECTED,
    UNKNOWN,
    ORDER_ALREADY_CLOSED,

    /** 결제가 이미 IN_PROGRESS/UNKNOWN 이 아니다(다른 흐름이 먼저 끝냈다). */
    ALREADY_SETTLED,
}

fun judge(outcome: PgOutcome, expectedAmount: Long): PgVerdict = when (outcome) {
    is PgOutcome.Failed -> PgVerdict.REJECT
    is PgOutcome.Unknown -> PgVerdict.HOLD
    is PgOutcome.Done -> when (outcome.payment.status) {
        PgPaymentStatus.DONE ->
            if (outcome.payment.totalAmount == expectedAmount) PgVerdict.APPROVE else PgVerdict.VOID_THEN_REJECT
        PgPaymentStatus.ABORTED, PgPaymentStatus.EXPIRED, PgPaymentStatus.CANCELED -> PgVerdict.REJECT
        PgPaymentStatus.OTHER, PgPaymentStatus.PARTIAL_CANCELED -> PgVerdict.HOLD
    }
}

/**
 * 결제 승인. PG 호출을 사이에 두고 트랜잭션을 둘로 나눈다(이 클래스에는 `@Transactional` 을 붙이지 않는다).
 * tx1 이 주문 행 락 아래에서 결제를 `IN_PROGRESS` 로 선점하고, PG 는 트랜잭션 밖에서 호출하고, tx2 가 다시 락을 잡고 마무리한다.
 */
@Service
class OrderPaymentService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val stockManager: StockManager,
    private val cartItemRemover: CartItemRemover,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    private sealed interface Claim {
        data class Done(val result: ConfirmPaymentResult) : Claim
        data class Started(val orderId: Long, val totalAmount: Long) : Claim
    }

    fun confirm(memberId: Long, cmd: ConfirmPaymentCommand): ConfirmPaymentResult {
        val claim = tx.run { claim(memberId, cmd) }
        if (claim is Claim.Done) return claim.result
        val started = claim as Claim.Started
        val orderId = started.orderId

        val outcome = paymentGateway.confirmWithPg(cmd.paymentKey, cmd.orderNo, cmd.amount)
        var verdict = judge(outcome, started.totalAmount)
        var failReason = failReasonOf(outcome)
        // 사용자에게는 거절(402)로 답하지만, PG 취소가 확인되지 않았으면 결제를 UNKNOWN 으로 남겨 대사가 다시 확인하게 한다.
        var rejectedButUnvoided = false
        if (verdict == PgVerdict.VOID_THEN_REJECT) {
            val pgTotal = (outcome as PgOutcome.Done).payment.totalAmount
            val voided = isCanceled(paymentGateway.cancelWithPg(cmd.paymentKey, pgTotal, "amount-mismatch:$orderId", "void-$orderId"))
            verdict = if (voided) PgVerdict.REJECT else PgVerdict.HOLD
            rejectedButUnvoided = !voided
            failReason = "amount-mismatch"
        }

        return when (finishApproval(orderId, cmd.paymentKey, verdict, failReason)) {
            ApprovalFinish.PAID -> ConfirmPaymentResult(orderId, cmd.orderNo, OrderStatus.PAID.name, "APPROVED")
            ApprovalFinish.REJECTED -> throw DomainException(ErrorCode.PAYMENT_REJECTED)
            ApprovalFinish.UNKNOWN -> if (rejectedButUnvoided) {
                throw DomainException(ErrorCode.PAYMENT_REJECTED)
            } else ConfirmPaymentResult(orderId, cmd.orderNo, OrderStatus.PENDING_PAYMENT.name, "UNKNOWN")
            ApprovalFinish.ORDER_ALREADY_CLOSED -> {
                if (verdict == PgVerdict.APPROVE) {
                    val voided = isCanceled(
                        paymentGateway.cancelWithPg(cmd.paymentKey, cmd.amount, "order-closed:$orderId", "void-$orderId"),
                    )
                    // 취소가 확인된 때만 결제를 닫는다. 아니면 UNKNOWN 으로 남겨 대사가 PG 를 다시 조회하게 한다.
                    tx.run {
                        if (voided) paymentGateway.voidAfterApproval(orderId)
                        else if (paymentGateway.get(orderId).status == "IN_PROGRESS") paymentGateway.markUnknown(orderId)
                    }
                }
                throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
            }
            ApprovalFinish.ALREADY_SETTLED -> settledResult(orderId)
        }
    }

    /** tx1: 결제 상태를 먼저 보고(같은 키의 재시도는 멱등), 그다음 주문 상태·금액을 확인한 뒤 결제를 IN_PROGRESS 로 선점한다. */
    private fun claim(memberId: Long, cmd: ConfirmPaymentCommand): Claim {
        val order = orderRepository.findByOrderNoForUpdate(cmd.orderNo)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val orderId = requireNotNull(order.id)
        val payment = paymentGateway.get(orderId)
        val sameKey = payment.paymentKey == cmd.paymentKey
        if (sameKey && (payment.status == "APPROVED" || payment.status == "UNKNOWN")) return Claim.Done(resultOf(order, payment))
        // 다른 키여도 선점된 결제가 PG 결과를 기다리는 중이거나 대사 대기 중이므로 새 승인을 시작하지 않는다.
        if (payment.status == "IN_PROGRESS" || payment.status == "UNKNOWN") throw DomainException(ErrorCode.PAYMENT_IN_PROGRESS)
        if (order.status != OrderStatus.PENDING_PAYMENT) throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        if (cmd.amount != order.totalAmount) throw DomainException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
        paymentGateway.start(orderId, cmd.paymentKey)
        return Claim.Started(orderId, order.totalAmount)
    }

    /**
     * tx2: 주문 행 락 아래에서 다시 읽은 현재 상태를 확인하고 마무리한다. 결제가 이미 IN_PROGRESS/UNKNOWN 이 아니면 아무것도 하지 않는다.
     * 승인 흐름과 정리 작업(대사)이 함께 쓴다.
     */
    fun finishApproval(orderId: Long, paymentKey: String, verdict: PgVerdict, failReason: String): ApprovalFinish = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val payment = paymentGateway.get(orderId)
        if ((payment.status != "IN_PROGRESS" && payment.status != "UNKNOWN") || payment.paymentKey != paymentKey) {
            return@run ApprovalFinish.ALREADY_SETTLED
        }
        if (order.status != OrderStatus.PENDING_PAYMENT) return@run ApprovalFinish.ORDER_ALREADY_CLOSED

        when (verdict) {
            PgVerdict.APPROVE -> {
                order.markPaid(LocalDateTime.now(clock))
                orderRepository.save(order)
                paymentGateway.approve(orderId)
                stockManager.increaseSales(order.allItems.groupBy { it.productId }.map { (productId, items) ->
                    ProductQuantity(productId, items.sumOf { it.quantity })
                })
                cartItemRemover.removeBySkuIds(order.memberId, order.allItems.map { it.skuId }.toSet())
                ApprovalFinish.PAID
            }
            PgVerdict.REJECT, PgVerdict.VOID_THEN_REJECT -> {
                paymentGateway.fail(orderId, failReason)
                order.cancelForPaymentFailure()
                orderRepository.save(order)
                stockManager.restore(order.skuQuantities.map { (skuId, quantity) -> SkuQuantity(skuId, quantity) })
                ApprovalFinish.REJECTED
            }
            PgVerdict.HOLD -> {
                paymentGateway.markUnknown(orderId)
                ApprovalFinish.UNKNOWN
            }
        }
    }

    /** 다른 흐름이 먼저 끝낸 결제의 현재 상태를 응답으로 바꾼다. */
    private fun settledResult(orderId: Long): ConfirmPaymentResult = tx.run {
        val order = orderRepository.findById(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val payment = paymentGateway.get(orderId)
        when (payment.status) {
            "APPROVED", "UNKNOWN" -> resultOf(order, payment)
            "FAILED" -> throw DomainException(ErrorCode.PAYMENT_REJECTED)
            else -> throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        }
    }

    private fun resultOf(order: Order, payment: PaymentInfo) =
        ConfirmPaymentResult(requireNotNull(order.id), order.orderNo, order.status.name, payment.status)

    private fun isCanceled(outcome: PgOutcome) =
        outcome is PgOutcome.Done && outcome.payment.status == PgPaymentStatus.CANCELED

    private fun failReasonOf(outcome: PgOutcome): String = when (outcome) {
        is PgOutcome.Failed -> outcome.code
        is PgOutcome.Done -> "PG status ${outcome.payment.status}"
        is PgOutcome.Unknown -> "PG unknown"
    }
}
