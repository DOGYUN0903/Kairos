package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.TransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.DecidedBy
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.ReturnReason
import com.dogyun.kairos.order.domain.ReturnRequest
import com.dogyun.kairos.order.domain.ReturnStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * 반품 요청·결정·수거 확인 환불(스펙 §4·§10, ADR 0005). 모든 상태 변경은 주문 행 락 아래에서 한다.
 * 판매자·관리자·작업 경로는 주문 id 를 트랜잭션 **밖에서** [OrderRepository.findOrderIdByReturnId] 로 찾고 안에서 잠근다
 * (락 전에 읽으면 REPEATABLE READ 스냅샷이 낡는다). 수거 확인은 [OrderCancelService] 와 같은 tx1 → PG(트랜잭션 밖) → tx2 구조이고
 * 이 클래스에는 `@Transactional` 을 붙이지 않는다.
 */
@Service
class OrderReturnService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val stockManager: StockManager,
    private val benefitReverter: BenefitReverter,
    private val pointEarner: PointEarner,
    private val properties: OrderProperties,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    private sealed interface Claim {
        /** 이미 환불 중이거나 끝난 반품(재요청). 현재 상태를 그대로 돌려준다. */
        data class Current(val result: ReturnRequestResult) : Claim
        data class Started(val orderId: Long, val itemId: Long, val paymentKey: String?, val refundAmount: Long) : Claim
    }

    fun request(memberId: Long, orderId: Long, orderItemId: Long, cmd: RequestReturnCommand): ReturnRequestResult {
        val reason = ReturnReason.entries.firstOrNull { it.name == cmd.reason } ?: throw DomainException(ErrorCode.RETURN_INVALID)
        return tx.run {
            val order = orderRepository.findByIdForUpdate(orderId)?.takeIf { it.memberId == memberId }
                ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
            order.requestReturn(orderItemId, reason, cmd.detail, properties.returnWindow, now())
            val saved = orderRepository.save(order)
            val returnId = requireNotNull(saved.findItem(orderItemId).returnRequest?.id) { "반품이 저장되지 않았다" }
            saved.returnResult(returnId)
        }
    }

    /** 구매자 철회. `REQUESTED` 일 때만, 이미 `WITHDRAWN` 이면 그대로 성공. */
    fun withdraw(memberId: Long, returnId: Long): ReturnRequestResult =
        change(returnId, check = { r -> if (r.memberId != memberId) throw notFound() }) { order -> order.withdrawReturn(returnId) }

    fun approveBySeller(sellerId: Long, returnId: Long): ReturnRequestResult =
        change(returnId, sellerCheck(sellerId)) { order -> order.approveReturn(returnId, DecidedBy.SELLER, now()) }

    fun rejectBySeller(sellerId: Long, returnId: Long, rejectReason: String): ReturnRequestResult =
        change(returnId, sellerCheck(sellerId)) { order -> order.rejectReturn(returnId, rejectReason, now()) }

    /** 거절된 반품도 판매자 주문이 `CONFIRMED` 되기 전이면 다시 승인한다(도메인이 판정). */
    fun approveByAdmin(returnId: Long): ReturnRequestResult =
        change(returnId, check = {}) { order -> order.approveReturn(returnId, DecidedBy.ADMIN, now()) }

    /** 승인 상태에서 중단한다. 이미 `CANCELED_BY_ADMIN` 이면 그대로 성공. */
    fun cancelByAdmin(returnId: Long): ReturnRequestResult =
        change(returnId, check = {}) { order ->
            if (order.findReturn(returnId).second.returnRequest?.status != ReturnStatus.CANCELED_BY_ADMIN) order.cancelReturnByAdmin(returnId)
        }

    /** 미응답 자동 승인(작업용). 주문 id 는 작업이 트랜잭션 밖에서 찾아 넘긴다. `REQUESTED` 가 아니면 false. */
    fun approveBySystem(orderId: Long, returnId: Long): Boolean = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: return@run false
        if (order.findReturn(returnId).second.returnRequest?.status != ReturnStatus.REQUESTED) return@run false
        order.approveReturn(returnId, DecidedBy.SYSTEM, now())
        orderRepository.save(order)
        true
    }

    fun receiveBySeller(sellerId: Long, returnId: Long): ReturnRequestResult = receive(returnId, sellerCheck(sellerId))

    fun receiveByAdmin(returnId: Long): ReturnRequestResult = receive(returnId, check = {})

    private fun sellerCheck(sellerId: Long): (ReturnRequest) -> Unit = { r -> if (r.sellerId != sellerId) throw notFound() }

    /** 주문 id 는 트랜잭션 밖에서 찾고, 락을 잡은 뒤 [check](소유 확인) → [action] → 저장. */
    private fun change(returnId: Long, check: (ReturnRequest) -> Unit, action: (Order) -> Unit): ReturnRequestResult {
        val orderId = orderIdOf(returnId)
        return tx.run {
            val order = lockOrder(orderId, returnId, check)
            action(order)
            orderRepository.save(order).returnResult(returnId)
        }
    }

    private fun orderIdOf(returnId: Long): Long = orderRepository.findOrderIdByReturnId(returnId) ?: throw notFound()

    private fun lockOrder(orderId: Long, returnId: Long, check: (ReturnRequest) -> Unit): Order {
        val order = orderRepository.findByIdForUpdate(orderId) ?: throw notFound()
        check(requireNotNull(order.findReturn(returnId).second.returnRequest))
        return order
    }

    private fun receive(returnId: Long, check: (ReturnRequest) -> Unit): ReturnRequestResult {
        val orderId = orderIdOf(returnId)
        val claim = tx.run { claim(orderId, returnId, check) }
        if (claim is Claim.Current) return claim.result
        val started = claim as Claim.Started

        val outcome = when {
            started.refundAmount == 0L -> NO_PG_CALL
            // 현금 환불이 있는데 paymentKey 가 없으면 환불할 수 없다. 환불 완료로 처리하지 않고 RETURN_REFUNDING 으로 두어
            // 대사(같은 조건이면 건너뜀)·운영자가 보게 한다.
            started.paymentKey == null -> PgOutcome.Unknown
            else -> paymentGateway.cancelWithPg(
                started.paymentKey, started.refundAmount, "orderItem:${started.itemId}", refundKey(started.itemId, started.refundAmount),
            )
        }

        val finish = finishReturn(orderId, returnId, outcome)
        if (finish == ReturnFinish.REVERTED) throw DomainException(ErrorCode.PAYMENT_CANCEL_FAILED)
        return tx.run { requireNotNull(orderRepository.findById(orderId)).returnResult(returnId) }
    }

    /**
     * tx1: 락 아래에서 이미 환불 중·완료면 현재 상태를 돌려주고(멱등), 아니면 환불 계획을 세워 `RETURN_REFUNDING` 으로 저장한다.
     * 승인 전이면 계산기가 `RETURN_INVALID_STATE_TRANSITION` 으로 막는다.
     */
    private fun claim(orderId: Long, returnId: Long, check: (ReturnRequest) -> Unit): Claim {
        val order = lockOrder(orderId, returnId, check)
        val item = order.findReturn(returnId).second
        val status = requireNotNull(item.returnRequest).status
        if (status == ReturnStatus.REFUNDING || status == ReturnStatus.COMPLETED) return Claim.Current(order.returnResult(returnId))
        val plan = order.planReturnRefund(returnId, properties.returnFeePolicy())
        order.startReturnRefund(returnId, plan, now())
        orderRepository.save(order)
        val paymentKey = if (plan.refundAmount > 0) paymentGateway.get(orderId).paymentKey else null
        return Claim.Started(orderId, requireNotNull(item.id), paymentKey, plan.refundAmount)
    }

    /**
     * tx2: 락 아래에서 다시 읽은 현재 상태로 마무리한다. 수거 확인과 정리 작업(대사)이 함께 쓴다.
     * 락 순서 orders → member_point → coupon → sku → product: 쿠폰·포인트 되돌림(과 적립) → 변심이면 재고 복구 → 판매량 감소.
     */
    fun finishReturn(orderId: Long, returnId: Long, outcome: PgOutcome): ReturnFinish = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val (sellerOrder, item) = order.findReturn(returnId)
        when (item.status) {
            OrderItemStatus.RETURNED -> return@run ReturnFinish.RETURNED
            OrderItemStatus.RETURN_APPROVED, OrderItemStatus.ORDERED -> return@run ReturnFinish.REVERTED
            OrderItemStatus.RETURN_REFUNDING -> Unit
            else -> return@run ReturnFinish.PENDING
        }
        if (order.status != OrderStatus.PAID) return@run ReturnFinish.PENDING
        val request = requireNotNull(item.returnRequest)
        val refundAmount = request.refundAmount
        when {
            refundAmount == 0L || isRefunded(outcome) -> {
                val plan = item.toReturnRefundPlan()
                val closed = order.completeReturn(returnId, plan, now())
                orderRepository.save(order)
                if (refundAmount > 0) paymentGateway.recordRefund(orderId, refundAmount)
                val itemId = requireNotNull(item.id)
                benefitReverter.revertItem(
                    orderId, order.memberId, itemId, plan.pointsRefund, plan.productCouponReturn,
                    if (order.allItemsClosed) order.cartCouponId else null,
                )
                // 전부 반품으로 닫힌 판매자 주문의 적립(남은 ORDERED 가 없어 0P). member_point 는 재고보다 먼저 잡는다.
                if (closed) pointEarner.earnForPurchase(order.memberId, requireNotNull(sellerOrder.id), sellerOrder.confirmedPaidAmount)
                if (request.reason == ReturnReason.CHANGE_OF_MIND) stockManager.restore(listOf(SkuQuantity(item.skuId, item.quantity)))
                stockManager.decreaseSales(listOf(ProductQuantity(item.productId, item.quantity)))
                ReturnFinish.RETURNED
            }
            outcome is PgOutcome.Failed -> {
                order.revertReturnRefund(returnId)
                orderRepository.save(order)
                ReturnFinish.REVERTED
            }
            // Unknown 이거나 취소가 반영되지 않은 응답: RETURN_REFUNDING 으로 두고 대사가 확정한다.
            else -> ReturnFinish.PENDING
        }
    }

    private fun isRefunded(outcome: PgOutcome) = outcome is PgOutcome.Done &&
        (outcome.payment.status == PgPaymentStatus.PARTIAL_CANCELED || outcome.payment.status == PgPaymentStatus.CANCELED)

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun notFound() = DomainException(ErrorCode.RETURN_NOT_FOUND)

    private companion object {
        /** 현금 환불 0원이면 PG 를 부르지 않는다. [finishReturn] 이 환불 0 을 먼저 판단하므로 내용은 쓰이지 않는다. */
        val NO_PG_CALL: PgOutcome = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 0, 0, emptyList()))
    }
}

/** 주문 안의 반품 하나를 결과로 옮긴다(상품명·수량은 항목에서). */
internal fun Order.returnResult(returnId: Long): ReturnRequestResult {
    val item = findReturn(returnId).second
    return requireNotNull(item.returnRequest).toResult(item.productName, item.quantity)
}

internal fun ReturnRequest.toResult(productName: String, quantity: Int) = ReturnRequestResult(
    returnId = requireNotNull(id), orderId = orderId, sellerOrderId = sellerOrderId, orderItemId = orderItemId,
    productName = productName, quantity = quantity, reason = reason.name, feeBearer = feeBearer.name, status = status.name,
    returnFee = returnFee, outboundFeeRecovered = outboundFeeRecovered, shippingFeeRefund = shippingFeeRefund,
    sellerBorneFee = sellerBorneFee, refundAmount = refundAmount, pointsRefund = pointsRefund, rejectReason = rejectReason,
    requestedAt = requestedAt, decidedAt = decidedAt, decidedBy = decidedBy?.name, receivedAt = receivedAt, completedAt = completedAt,
)

internal fun ReturnRequest.toSummary() =
    ReturnRequestSummary(requireNotNull(id), reason.name, status.name, feeBearer.name, refundAmount, requestedAt)
