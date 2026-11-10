package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.CancelActor
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/** 취소 마무리(tx2)의 결과. 정리 작업(PG 조회 대사)도 같은 값을 쓴다. */
enum class CancelFinish { CANCELED, PENDING, REVERTED }

/**
 * 주문 항목 취소와 부분 환불(스펙 5장). PG 호출을 사이에 두고 트랜잭션을 둘로 나눈다(이 클래스에는 `@Transactional` 을 붙이지 않는다).
 * tx1 이 주문 행 락 아래에서 항목을 `CANCEL_REQUESTED` 로 바꿔 환불 계획을 저장하고, PG 부분 취소는 트랜잭션 밖에서 호출하고,
 * tx2 가 다시 락을 잡고 확정(또는 되돌림)한다. 환불 금액은 도메인([Order.planCancel])이 한 곳에서 계산한다.
 */
@Service
class OrderCancelService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val stockManager: StockManager,
    private val properties: OrderProperties,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    private sealed interface Claim {
        /** 이미 요청됐거나 끝난 항목(재요청). 현재 상태를 그대로 돌려준다. */
        data class Current(val result: CancelItemResult) : Claim
        data class Started(val orderId: Long, val paymentKey: String?, val refundAmount: Long) : Claim
    }

    fun cancelByBuyer(memberId: Long, orderId: Long, orderItemId: Long, reason: String): CancelItemResult {
        validate(reason)
        return cancel(CancelActor.BUYER, orderItemId, reason, lookup = {
            val order = orderRepository.findByIdForUpdate(orderId)?.takeIf { it.memberId == memberId }
                ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
            order to order.findItem(orderItemId)
        })
    }

    fun cancelBySeller(sellerId: Long, sellerOrderId: Long, orderItemId: Long, reason: String): CancelItemResult {
        validate(reason)
        // 주문 id 는 트랜잭션 밖에서 찾는다(락 전에 스냅샷이 만들어지지 않게, OrderRepository.findOrderIdBySellerOrderId 참고).
        val orderId = orderRepository.findOrderIdBySellerOrderId(sellerOrderId)
            ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        return cancel(CancelActor.SELLER, orderItemId, reason, lookup = {
            val order = orderRepository.findByIdForUpdate(orderId)
                ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
            val sellerOrder = order.findSellerOrder(sellerOrderId)
            // 결제 전 판매자 주문은 판매자에게 없는 것으로 친다(조회 API 와 같다).
            if (sellerOrder.sellerId != sellerId || sellerOrder.status == SellerOrderStatus.PENDING_PAYMENT) {
                throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
            }
            order to (sellerOrder.items.firstOrNull { it.id == orderItemId } ?: throw DomainException(ErrorCode.ORDER_ITEM_NOT_FOUND))
        })
    }

    fun cancelByAdmin(orderId: Long, orderItemId: Long, reason: String): CancelItemResult {
        validate(reason)
        return cancel(CancelActor.ADMIN, orderItemId, reason, lookup = {
            val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
            order to order.findItem(orderItemId)
        })
    }

    private fun validate(reason: String) {
        if (reason.isBlank() || reason.length > MAX_REASON) throw DomainException(ErrorCode.ORDER_INVALID)
    }

    private fun cancel(
        actor: CancelActor,
        itemId: Long,
        reason: String,
        lookup: () -> Pair<Order, OrderItem>,
    ): CancelItemResult {
        val claim = tx.run { claim(actor, itemId, reason, lookup) }
        if (claim is Claim.Current) return claim.result
        val started = claim as Claim.Started

        val outcome = if (started.refundAmount > 0) {
            paymentGateway.cancelWithPg(
                requireNotNull(started.paymentKey) { "승인된 결제에 paymentKey 가 없다: order ${started.orderId}" },
                started.refundAmount, "orderItem:$itemId", refundKey(itemId, started.refundAmount),
            )
        } else NO_PG_CALL

        val finish = finishCancel(started.orderId, itemId, outcome)
        if (finish == CancelFinish.REVERTED) throw DomainException(ErrorCode.PAYMENT_CANCEL_FAILED)
        return tx.run { resultOf(requireNotNull(orderRepository.findById(started.orderId)).findItem(itemId)) }
    }

    /**
     * tx1: 주문 행 락 아래에서 항목이 이미 요청/취소됐으면 현재 상태를 돌려주고(멱등, 도메인 호출 전에),
     * 아니면 환불 계획을 세워 항목을 `CANCEL_REQUESTED` 로 저장한다.
     */
    private fun claim(actor: CancelActor, itemId: Long, reason: String, lookup: () -> Pair<Order, OrderItem>): Claim {
        val (order, item) = lookup()
        if (item.status != OrderItemStatus.ORDERED) return Claim.Current(resultOf(item))
        val plan = order.planCancel(itemId, actor, properties.shippingPolicy())
        order.requestCancel(itemId, actor, reason, plan)
        orderRepository.save(order)
        val orderId = requireNotNull(order.id)
        val paymentKey = if (plan.refundAmount > 0) paymentGateway.get(orderId).paymentKey else null
        return Claim.Started(orderId, paymentKey, plan.refundAmount)
    }

    /**
     * tx2: 주문 행 락 아래에서 다시 읽은 현재 상태를 확인하고 마무리한다. 항목이 아직 `CANCEL_REQUESTED` 이고 주문·판매자 주문이
     * `PAID` 일 때만 움직인다(아니면 현재 상태를 돌려주는 no-op). 승인 흐름과 정리 작업(대사)이 함께 쓴다.
     */
    fun finishCancel(orderId: Long, orderItemId: Long, outcome: PgOutcome): CancelFinish = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val item = order.findItem(orderItemId)
        when (item.status) {
            OrderItemStatus.CANCELED -> return@run CancelFinish.CANCELED
            OrderItemStatus.ORDERED -> return@run CancelFinish.REVERTED
            OrderItemStatus.CANCEL_REQUESTED -> Unit
        }
        val sellerOrder = order.sellerOrders.first { so -> so.items.any { it.id == orderItemId } }
        if (order.status != OrderStatus.PAID || sellerOrder.status != SellerOrderStatus.PAID) return@run CancelFinish.PENDING

        val refundAmount = item.refundAmount
        when {
            refundAmount == 0L || isRefunded(outcome) -> {
                order.confirmCancel(orderItemId, item.toRefundPlan(), LocalDateTime.now(clock))
                orderRepository.save(order)
                if (refundAmount > 0) paymentGateway.recordRefund(orderId, refundAmount)
                stockManager.restore(listOf(SkuQuantity(item.skuId, item.quantity)))
                stockManager.decreaseSales(listOf(ProductQuantity(item.productId, item.quantity)))
                CancelFinish.CANCELED
            }
            outcome is PgOutcome.Failed -> {
                order.revertCancel(orderItemId)
                orderRepository.save(order)
                CancelFinish.REVERTED
            }
            // Unknown, 또는 취소가 반영되지 않은 것으로 보이는 응답: 환불 여부를 알 수 없으니 그대로 두고 대사가 확정한다.
            else -> CancelFinish.PENDING
        }
    }

    private fun isRefunded(outcome: PgOutcome) = outcome is PgOutcome.Done &&
        (outcome.payment.status == PgPaymentStatus.PARTIAL_CANCELED || outcome.payment.status == PgPaymentStatus.CANCELED)

    private fun resultOf(item: OrderItem) =
        CancelItemResult(requireNotNull(item.id), item.status.name, item.refundAmount, item.shippingFeeDeducted)

    companion object {
        private const val MAX_REASON = 500

        /** 환불이 0원이면 PG 를 부르지 않는다. [finishCancel] 이 환불 0 을 먼저 판단하므로 이 값의 내용은 쓰이지 않는다. */
        private val NO_PG_CALL: PgOutcome = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 0, 0, emptyList()))
    }
}
