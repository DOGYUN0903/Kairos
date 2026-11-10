package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * PG 멱등 키는 요청 본문이 같을 때만 같아야 하므로 금액을 넣는다(ADR 0003). 승인 흐름과 대사는 저장된 값으로 같은 키를 만든다.
 */
fun refundKey(orderItemId: Long, refundAmount: Long) = "refund-$orderItemId-$refundAmount"

fun voidKey(orderId: Long, amount: Long) = "void-$orderId-$amount"

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
    private val benefitReverter: BenefitReverter,
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
            verdict = voidMismatch(orderId, cmd.paymentKey, pgTotal)
            rejectedButUnvoided = verdict == PgVerdict.HOLD
            failReason = "amount-mismatch"
        }

        return when (finishApproval(orderId, cmd.paymentKey, verdict, failReason)) {
            ApprovalFinish.PAID -> ConfirmPaymentResult(orderId, cmd.orderNo, OrderStatus.PAID.name, "APPROVED")
            ApprovalFinish.REJECTED -> throw DomainException(ErrorCode.PAYMENT_REJECTED)
            ApprovalFinish.UNKNOWN -> if (rejectedButUnvoided) {
                throw DomainException(ErrorCode.PAYMENT_REJECTED)
            } else ConfirmPaymentResult(orderId, cmd.orderNo, OrderStatus.PENDING_PAYMENT.name, "UNKNOWN")
            ApprovalFinish.ORDER_ALREADY_CLOSED -> {
                if (verdict == PgVerdict.APPROVE) voidClosedOrder(orderId, cmd.paymentKey, cmd.amount)
                throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
            }
            ApprovalFinish.ALREADY_SETTLED -> settledResult(orderId)
        }
    }

    /**
     * 금액이 다른 PG 승인을 전액 취소한다(승인 흐름과 대사가 함께 쓴다). 취소가 확인되면 [PgVerdict.REJECT],
     * 확인되지 않으면 [PgVerdict.HOLD] 로 결제를 UNKNOWN 으로 남겨 대사가 다시 확인하게 한다. PG 호출이라 트랜잭션 밖이다.
     */
    fun voidMismatch(orderId: Long, paymentKey: String, pgTotal: Long): PgVerdict {
        val voided = isCanceled(paymentGateway.cancelWithPg(paymentKey, pgTotal, "amount-mismatch:$orderId", voidKey(orderId, pgTotal)))
        return if (voided) PgVerdict.REJECT else PgVerdict.HOLD
    }

    /**
     * 이미 닫힌 주문의 PG 결제를 같은 멱등 키로 [amount] 만큼 취소한다(승인 흐름과 대사가 함께 쓴다). PG 호출은 트랜잭션 밖이고,
     * 결제 상태는 주문 행 락 아래에서 다시 읽어 바꾼다. 취소가 확인되면 `IN_PROGRESS`/`UNKNOWN` 결제를 CANCELED 로 닫고,
     * 확인되지 않으면 `IN_PROGRESS` 결제를 UNKNOWN 으로 남긴다. 그 밖의 상태(FAILED, CANCELED 등)는 건드리지 않는다. 취소 확인 여부를 돌려준다.
     */
    fun voidClosedOrder(orderId: Long, paymentKey: String, amount: Long): Boolean {
        val voided = isCanceled(paymentGateway.cancelWithPg(paymentKey, amount, "order-closed:$orderId", voidKey(orderId, amount)))
        tx.run {
            orderRepository.findByIdForUpdate(orderId) ?: return@run
            val payment = paymentGateway.get(orderId)
            if (payment.paymentKey != paymentKey) return@run
            if (voided) {
                if (payment.status == "IN_PROGRESS" || payment.status == "UNKNOWN") paymentGateway.voidAfterApproval(orderId)
            } else if (payment.status == "IN_PROGRESS") paymentGateway.markUnknown(orderId)
        }
        return voided
    }

    /**
     * 닫힌 주문의 `FAILED`/`UNKNOWN` 결제에 PG 잔액이 없음이 확인됐을 때 결제를 닫는다(재취소 후보에서 빠진다).
     * 주문 행 락 아래에서 주문이 여전히 CANCELED 이고 결제 상태·키가 그대로일 때만 바꾼다.
     */
    fun closeVoidedPayment(orderId: Long, paymentKey: String) {
        tx.run {
            val order = orderRepository.findByIdForUpdate(orderId) ?: return@run
            if (order.status != OrderStatus.CANCELED) return@run
            val payment = paymentGateway.get(orderId)
            if (payment.paymentKey != paymentKey || (payment.status != "FAILED" && payment.status != "UNKNOWN")) return@run
            paymentGateway.markVoided(orderId)
        }
    }

    /** tx1: 결제 상태를 먼저 보고(같은 키의 재시도는 멱등), 그다음 주문 상태·금액을 확인한 뒤 결제를 IN_PROGRESS 로 선점한다. */
    private fun claim(memberId: Long, cmd: ConfirmPaymentCommand): Claim {
        val order = orderRepository.findByOrderNoForUpdate(cmd.orderNo)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val orderId = requireNotNull(order.id)
        val payment = paymentGateway.get(orderId)
        // 0원 주문은 PG 없이 이미 승인됐다(paymentKey 없음). 결제창에서 다시 승인을 보내도 그 결과를 돌려준다(스펙 7-1).
        if (payment.paymentKey == null && payment.status == "APPROVED" && order.status == OrderStatus.PAID) {
            return Claim.Done(resultOf(order, payment))
        }
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
                paymentGateway.approve(orderId)
                applyApproval(order)
                ApprovalFinish.PAID
            }
            PgVerdict.REJECT, PgVerdict.VOID_THEN_REJECT -> {
                paymentGateway.fail(orderId, failReason)
                order.cancelForPaymentFailure()
                orderRepository.save(order)
                // 쿠폰·포인트를 재고보다 먼저 되돌린다(락 순서: orders → member_point → coupon → sku).
                benefitReverter.revertAll(orderId, order.memberId, order.usedCouponIds(), order.pointsUsed)
                stockManager.restore(order.skuQuantities.map { (skuId, quantity) -> SkuQuantity(skuId, quantity) })
                ApprovalFinish.REJECTED
            }
            PgVerdict.HOLD -> {
                paymentGateway.markUnknown(orderId)
                ApprovalFinish.UNKNOWN
            }
        }
    }

    /**
     * 0원 주문의 승인 확정(ADR 0004). 주문 생성 트랜잭션이 커밋된 뒤 불린다. 주문 행 락 아래에서 결제가 `READY`·0원이고
     * 주문이 `PENDING_PAYMENT` 일 때만 PG 없이 승인하고 PG 승인과 같은 마무리([applyApproval])를 한다. 아니면 [ApprovalFinish.ALREADY_SETTLED].
     */
    fun approveWithoutPg(orderId: Long): ApprovalFinish = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val payment = paymentGateway.get(orderId)
        if (payment.status != "READY" || payment.amount != 0L || order.status != OrderStatus.PENDING_PAYMENT) {
            return@run ApprovalFinish.ALREADY_SETTLED
        }
        paymentGateway.approveWithoutPg(orderId)
        applyApproval(order)
        ApprovalFinish.PAID
    }

    /** 승인 뒤 처리: 주문 PAID·paidAt, 판매량 증가, 장바구니 항목 삭제. PG 승인과 0원 승인이 같은 단계를 쓴다. 주문 행 락 아래에서 부른다. */
    private fun applyApproval(order: Order) {
        order.markPaid(LocalDateTime.now(clock))
        orderRepository.save(order)
        stockManager.increaseSales(order.allItems.groupBy { it.productId }.map { (productId, items) ->
            ProductQuantity(productId, items.sumOf { it.quantity })
        })
        cartItemRemover.removeBySkuIds(order.memberId, order.allItems.map { it.skuId }.toSet())
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
