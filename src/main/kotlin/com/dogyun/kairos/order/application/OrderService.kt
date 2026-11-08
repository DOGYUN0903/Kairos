package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

// 결정 기록: docs/adr/0001-phase1-order-single-transaction.md (Phase 4에서 Saga로 대체)
/**
 * 주문 생성. Phase 1 에서는 재고(deal)·주문(order)·결제(payment) 세 애그리거트를 한 트랜잭션에서 바꾼다
 * (규칙 "애그리거트당 한 트랜잭션"의 의도적 예외, 설계 문서 9.4).
 * - 재고 차감에 락을 쓰지 않는다(Phase 2 에서 초과 판매를 재현·측정).
 * - 결제는 동기 호출하며, 실패/시간초과는 예외가 아니다: 주문은 CANCELLED 로, 재고는 복구된 채로 커밋된다.
 */
@Service
class OrderService(
    private val dealReader: DealReader,
    private val stockManager: StockManager,
    private val paymentProcessor: PaymentProcessor,
    private val orderRepository: OrderRepository,
    private val orderMetrics: OrderMetrics,
) {

    /**
     * 요청 하나당 `recordPlaced` 를 정확히 1회 기록한다(성공·거절·예외 모두; 예외는 기록 후 다시 던진다).
     * 카운터는 트랜잭션과 무관하므로 거절(롤백)도 세지만, 순판매량(sold)은 PAID 가 커밋된 뒤에만 반영한다.
     */
    @Transactional
    fun place(command: PlaceOrderCommand): PlaceOrderResult {
        val placed = try {
            doPlace(command)
        } catch (e: DomainException) {
            orderMetrics.recordPlaced(outcomeOf(e.errorCode))
            throw e
        } catch (e: Exception) {
            orderMetrics.recordPlaced(PlaceOutcome.OTHER)
            throw e
        }
        orderMetrics.recordPlaced(outcomeOf(placed.result))
        if (placed.result.status == OrderStatus.PAID) {
            afterCommit {
                orderMetrics.registerDeal(command.dealId, placed.totalQuantity)
                orderMetrics.addSold(command.dealId, command.quantity)
            }
        }
        return placed.result
    }

    private class Placed(val result: PlaceOrderResult, val totalQuantity: Int)

    private fun doPlace(command: PlaceOrderCommand): Placed {
        // 1. 수량 검증
        if (command.quantity < 1) throw DomainException(ErrorCode.INVALID_REQUEST)

        // 2. 딜 스냅샷 (없으면 DEAL_NOT_FOUND) + 진행 여부
        val deal = dealReader.getSnapshot(command.dealId)
        if (!deal.ongoing) throw DomainException(ErrorCode.DEAL_NOT_ONGOING)

        // 3. 인당 제한 (취소 주문 제외)
        val ordered = orderRepository.sumActiveQuantity(command.memberId, command.dealId)
        if (ordered + command.quantity > deal.perMemberLimit) throw DomainException(ErrorCode.ORDER_LIMIT_EXCEEDED)

        // 4. 재고 차감 (락 없음, 부족하면 STOCK_SOLD_OUT)
        stockManager.decrease(command.dealId, command.quantity)

        // 5. 주문 저장 (CREATED) — 결제에 넘길 orderId 확보
        val created = orderRepository.save(
            Order.create(command.memberId, command.dealId, command.quantity, Money(deal.dealPrice)),
        )
        val orderId = checkNotNull(created.id)

        // 6. 동기 결제
        when (val result = paymentProcessor.pay(orderId, created.amount.amount)) {
            is PaymentResult.Approved -> created.markPaid()
            is PaymentResult.Failed -> {
                created.cancel(result.reason)
                stockManager.restore(command.dealId, command.quantity)
            }
        }

        // 7. 저장 후 결과 반환 (결제 실패도 커밋)
        val saved = orderRepository.save(created)
        return Placed(PlaceOrderResult(orderId, saved.status, saved.cancelReason), deal.totalQuantity)
    }

    private fun outcomeOf(result: PlaceOrderResult): PlaceOutcome = when (result.cancelReason) {
        null -> PlaceOutcome.PAID
        CancelReason.PAYMENT_FAILED -> PlaceOutcome.PAYMENT_FAILED
        CancelReason.PAYMENT_TIMEOUT -> PlaceOutcome.PAYMENT_TIMEOUT
        CancelReason.USER_REQUESTED -> PlaceOutcome.OTHER // place 에서는 발생하지 않는다
    }

    private fun outcomeOf(errorCode: ErrorCode): PlaceOutcome = when (errorCode) {
        ErrorCode.STOCK_SOLD_OUT -> PlaceOutcome.SOLD_OUT
        ErrorCode.ORDER_LIMIT_EXCEEDED -> PlaceOutcome.LIMIT_EXCEEDED
        ErrorCode.DEAL_NOT_ONGOING -> PlaceOutcome.DEAL_NOT_ONGOING
        else -> PlaceOutcome.OTHER
    }

    /** 활성 트랜잭션이 있으면 커밋 후에, 없으면(단위 테스트 등) 즉시 실행한다. 롤백되면 실행되지 않는다. */
    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }

    /**
     * 본인 주문 취소. 남의 주문·없는 주문은 존재를 드러내지 않고 ORDER_NOT_FOUND.
     * PAID 는 환불 후 취소, CREATED 는 환불 없이 취소하고 둘 다 재고를 복구한다(딜 종료 후에도 동일).
     * 이미 취소된 주문은 ORDER_ALREADY_CANCELLED 이며 환불·재고에 손대지 않는다.
     */
    @Transactional
    fun cancel(memberId: Long, orderId: Long) {
        val order = orderRepository.findById(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        if (order.status == OrderStatus.CANCELLED) throw DomainException(ErrorCode.ORDER_ALREADY_CANCELLED)

        val wasPaid = order.status == OrderStatus.PAID
        if (wasPaid) paymentProcessor.refund(orderId)
        order.cancel(CancelReason.USER_REQUESTED)
        stockManager.restore(order.dealId, order.quantity)
        orderRepository.save(order)
        // 순판매량은 커밋된 PAID 수량의 누적이므로, PAID 였던 주문의 취소만 커밋 후 차감한다.
        if (wasPaid) afterCommit { orderMetrics.subtractSold(order.dealId, order.quantity) }
    }
}
