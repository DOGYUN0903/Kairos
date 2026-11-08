package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderServiceTest {

    private class FakeDealReader(var snapshot: DealSnapshot?) : DealReader {
        override fun getSnapshot(dealId: Long): DealSnapshot =
            snapshot?.takeIf { it.dealId == dealId } ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)
    }

    private class FakeStockManager(var remaining: Int) : StockManager {
        override fun decrease(dealId: Long, quantity: Int) {
            if (quantity > remaining) throw DomainException(ErrorCode.STOCK_SOLD_OUT)
            remaining -= quantity
        }

        override fun restore(dealId: Long, quantity: Int) {
            remaining += quantity
        }
    }

    private class FakePaymentProcessor(var result: PaymentResult) : PaymentProcessor {
        val calls = mutableListOf<Pair<Long, Long>>()
        override fun pay(orderId: Long, amount: Long): PaymentResult {
            calls += orderId to amount
            return result
        }
    }

    private class FakeOrderRepository : OrderRepository {
        val store = mutableMapOf<Long, Order>()
        override fun save(order: Order): Order {
            val saved = if (order.id == null) {
                Order.restore(store.size + 1L, order.memberId, order.dealId, order.quantity, order.amount, order.status, order.cancelReason)
            } else {
                order
            }
            store[checkNotNull(saved.id)] = saved
            return saved
        }

        override fun findById(id: Long): Order? = store[id]

        override fun sumActiveQuantity(memberId: Long, dealId: Long): Int =
            store.values.filter { it.memberId == memberId && it.dealId == dealId && it.status != OrderStatus.CANCELLED }
                .sumOf { it.quantity }

        override fun findByMemberId(memberId: Long): List<Order> = store.values.filter { it.memberId == memberId }
    }

    private val dealReader = FakeDealReader(DealSnapshot(dealId = 10L, dealPrice = 5000, perMemberLimit = 2, ongoing = true))
    private val stockManager = FakeStockManager(remaining = 5)
    private val paymentProcessor = FakePaymentProcessor(PaymentResult.Approved(paymentId = 77L))
    private val orderRepository = FakeOrderRepository()
    private val service = OrderService(dealReader, stockManager, paymentProcessor, orderRepository)

    private fun place(quantity: Int = 1, memberId: Long = 1L) = service.place(PlaceOrderCommand(memberId, 10L, quantity))

    private fun existing(memberId: Long, quantity: Int, status: OrderStatus, reason: CancelReason? = null) {
        val order = Order.create(memberId, 10L, quantity, Money(5000))
        when (status) {
            OrderStatus.PAID -> order.markPaid()
            OrderStatus.CANCELLED -> order.cancel(checkNotNull(reason))
            OrderStatus.CREATED -> Unit
        }
        orderRepository.save(order)
    }

    @Test
    fun 결제_승인시_PAID_재고_차감() {
        val result = place(quantity = 2)

        assertEquals(OrderStatus.PAID, result.status)
        assertNull(result.cancelReason)
        assertEquals(3, stockManager.remaining)
        val saved = checkNotNull(orderRepository.findById(result.orderId))
        assertEquals(OrderStatus.PAID, saved.status)
        assertEquals(Money(10000), saved.amount)
        assertEquals(listOf(result.orderId to 10000L), paymentProcessor.calls)
    }

    @Test
    fun 결제_실패시_주문은_취소로_남고_재고는_복구된다() {
        paymentProcessor.result = PaymentResult.Failed(CancelReason.PAYMENT_TIMEOUT)

        val result = place(quantity = 2)

        assertEquals(OrderStatus.CANCELLED, result.status)
        assertEquals(CancelReason.PAYMENT_TIMEOUT, result.cancelReason)
        assertEquals(5, stockManager.remaining)
        val saved = checkNotNull(orderRepository.findById(result.orderId))
        assertEquals(OrderStatus.CANCELLED, saved.status)
        assertEquals(CancelReason.PAYMENT_TIMEOUT, saved.cancelReason)
    }

    @Test
    fun 제한_초과_주문은_거절된다() {
        existing(memberId = 1L, quantity = 1, status = OrderStatus.PAID)

        val e = assertFailsWith<DomainException> { place(quantity = 2) }

        assertEquals(ErrorCode.ORDER_LIMIT_EXCEEDED, e.errorCode)
        assertEquals(5, stockManager.remaining)
        assertTrue(paymentProcessor.calls.isEmpty())
        assertEquals(1, orderRepository.store.size)
    }

    @Test
    fun 취소된_주문은_제한에서_제외된다() {
        existing(memberId = 1L, quantity = 2, status = OrderStatus.CANCELLED, reason = CancelReason.PAYMENT_FAILED)

        val result = place(quantity = 2)

        assertEquals(OrderStatus.PAID, result.status)
        assertEquals(3, stockManager.remaining)
    }

    @Test
    fun 다른_회원의_주문은_제한에_영향을_주지_않는다() {
        existing(memberId = 2L, quantity = 2, status = OrderStatus.PAID)

        assertEquals(OrderStatus.PAID, place(quantity = 2, memberId = 1L).status)
    }

    @Test
    fun 진행중이_아닌_딜은_주문할_수_없다() {
        dealReader.snapshot = DealSnapshot(10L, 5000, 2, ongoing = false)

        val e = assertFailsWith<DomainException> { place() }

        assertEquals(ErrorCode.DEAL_NOT_ONGOING, e.errorCode)
        assertEquals(5, stockManager.remaining)
        assertTrue(orderRepository.store.isEmpty())
    }

    @Test
    fun 재고_부족이면_STOCK_SOLD_OUT() {
        stockManager.remaining = 1

        val e = assertFailsWith<DomainException> { place(quantity = 2) }

        assertEquals(ErrorCode.STOCK_SOLD_OUT, e.errorCode)
        assertTrue(paymentProcessor.calls.isEmpty())
        assertTrue(orderRepository.store.isEmpty())
    }

    @Test
    fun 수량이_1_미만이면_INVALID_REQUEST() {
        val e = assertFailsWith<DomainException> { place(quantity = 0) }
        assertEquals(ErrorCode.INVALID_REQUEST, e.errorCode)
        assertEquals(5, stockManager.remaining)
    }

    @Test
    fun 없는_딜은_DEAL_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { service.place(PlaceOrderCommand(1L, 999L, 1)) }
        assertEquals(ErrorCode.DEAL_NOT_FOUND, e.errorCode)
    }
}
