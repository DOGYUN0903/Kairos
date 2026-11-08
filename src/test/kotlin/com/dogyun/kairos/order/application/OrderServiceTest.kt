package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.common.domain.PageResult
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

        override fun getProductNames(dealIds: Collection<Long>): Map<Long, String> = dealIds.associateWith { "상품$it" }
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
        val refunds = mutableListOf<Long>()
        override fun refund(orderId: Long) {
            refunds += orderId
        }

        override fun pay(orderId: Long, amount: Long): PaymentResult {
            calls += orderId to amount
            return result
        }
    }

    private class FakeOrderMetrics : OrderMetrics {
        val placed = mutableListOf<PlaceOutcome>()
        val totals = mutableMapOf<Long, Int>()
        val sold = mutableMapOf<Long, Long>()
        override fun recordPlaced(result: PlaceOutcome) {
            placed += result
        }

        override fun registerDeal(dealId: Long, totalQuantity: Int) {
            totals[dealId] = totalQuantity
        }

        override fun addSold(dealId: Long, quantity: Int) {
            sold.merge(dealId, quantity.toLong(), Long::plus)
        }

        override fun subtractSold(dealId: Long, quantity: Int) {
            sold.merge(dealId, -quantity.toLong(), Long::plus)
        }
    }

    private class FakeOrderRepository : OrderRepository {
        val store = mutableMapOf<Long, Order>()
        override fun save(order: Order): Order {
            val saved = if (order.id == null) {
                Order.restore(store.size + 1L, order.memberId, order.dealId, order.quantity, order.amount, order.status, order.cancelReason, java.time.LocalDateTime.now())
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

        override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order> {
            val all = store.values.filter { it.memberId == memberId }.sortedByDescending { it.id }
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }
    }

    private val dealReader = FakeDealReader(DealSnapshot(dealId = 10L, dealPrice = 5000, perMemberLimit = 2, ongoing = true, totalQuantity = 5))
    private val stockManager = FakeStockManager(remaining = 5)
    private val paymentProcessor = FakePaymentProcessor(PaymentResult.Approved(paymentId = 77L))
    private val orderRepository = FakeOrderRepository()
    private val metrics = FakeOrderMetrics()
    private val service = OrderService(dealReader, stockManager, paymentProcessor, orderRepository, metrics)

    private fun place(quantity: Int = 1, memberId: Long = 1L) = service.place(PlaceOrderCommand(memberId, 10L, quantity))

    private val queryService = OrderQueryService(orderRepository, dealReader)

    private fun existing(memberId: Long, quantity: Int, status: OrderStatus, reason: CancelReason? = null): Long {
        val order = Order.create(memberId, 10L, quantity, Money(5000))
        when (status) {
            OrderStatus.PAID -> order.markPaid()
            OrderStatus.CANCELLED -> order.cancel(checkNotNull(reason))
            OrderStatus.CREATED -> Unit
        }
        return checkNotNull(orderRepository.save(order).id)
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
        dealReader.snapshot = DealSnapshot(10L, 5000, 2, ongoing = false, totalQuantity = 5)

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

    @Test
    fun 결제완료_주문_취소시_환불되고_재고_복구() {
        val placed = place(quantity = 2)
        assertEquals(3, stockManager.remaining)

        service.cancel(1L, placed.orderId)

        val saved = checkNotNull(orderRepository.findById(placed.orderId))
        assertEquals(OrderStatus.CANCELLED, saved.status)
        assertEquals(CancelReason.USER_REQUESTED, saved.cancelReason)
        assertEquals(listOf(placed.orderId), paymentProcessor.refunds)
        assertEquals(5, stockManager.remaining)
    }

    @Test
    fun 이미_취소된_주문은_다시_취소할_수_없다() {
        val placed = place(quantity = 2)
        service.cancel(1L, placed.orderId)

        val e = assertFailsWith<DomainException> { service.cancel(1L, placed.orderId) }

        assertEquals(ErrorCode.ORDER_ALREADY_CANCELLED, e.errorCode)
        assertEquals(5, stockManager.remaining)
        assertEquals(1, paymentProcessor.refunds.size)
    }

    @Test
    fun 결제실패로_취소된_주문의_재취소는_409이고_재고는_그대로() {
        val id = existing(1L, 2, OrderStatus.CANCELLED, CancelReason.PAYMENT_FAILED)

        val e = assertFailsWith<DomainException> { service.cancel(1L, id) }

        assertEquals(ErrorCode.ORDER_ALREADY_CANCELLED, e.errorCode)
        assertEquals(5, stockManager.remaining)
        assertTrue(paymentProcessor.refunds.isEmpty())
    }

    @Test
    fun 남의_주문_취소는_ORDER_NOT_FOUND() {
        val placed = place(quantity = 2, memberId = 1L)

        val e = assertFailsWith<DomainException> { service.cancel(2L, placed.orderId) }

        assertEquals(ErrorCode.ORDER_NOT_FOUND, e.errorCode)
        assertEquals(3, stockManager.remaining)
        assertTrue(paymentProcessor.refunds.isEmpty())
    }

    @Test
    fun 없는_주문_취소는_ORDER_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { service.cancel(1L, 999L) }
        assertEquals(ErrorCode.ORDER_NOT_FOUND, e.errorCode)
    }

    @Test
    fun CREATED_주문은_환불없이_취소되고_재고_복구() {
        stockManager.remaining = 3
        val id = existing(1L, 2, OrderStatus.CREATED)

        service.cancel(1L, id)

        assertEquals(OrderStatus.CANCELLED, checkNotNull(orderRepository.findById(id)).status)
        assertTrue(paymentProcessor.refunds.isEmpty())
        assertEquals(5, stockManager.remaining)
    }

    @Test
    fun 내_주문만_최신순으로_조회된다() {
        val first = existing(1L, 1, OrderStatus.PAID)
        existing(2L, 1, OrderStatus.PAID)
        val third = existing(1L, 2, OrderStatus.CANCELLED, CancelReason.USER_REQUESTED)

        val result = queryService.findMine(1L, page = 0, size = 10)

        assertEquals(listOf(third, first), result.content.map { it.orderId })
        assertEquals(2L, result.totalElements)
        val newest = result.content.first()
        assertEquals("상품10", newest.productName)
        assertEquals(10000L, newest.totalAmount)
        assertEquals(OrderStatus.CANCELLED, newest.status)
        assertEquals(CancelReason.USER_REQUESTED, newest.cancelReason)
    }

    @Test
    fun 조회_페이징() {
        repeat(3) { existing(1L, 1, OrderStatus.PAID) }

        val result = queryService.findMine(1L, page = 0, size = 2)

        assertEquals(2, result.content.size)
        assertTrue(result.hasNext)
        assertEquals(2, result.totalPages)
    }

    @Test
    fun 조회_잘못된_페이지는_INVALID_REQUEST() {
        assertEquals(ErrorCode.INVALID_REQUEST, assertFailsWith<DomainException> { queryService.findMine(1L, -1, 10) }.errorCode)
        assertEquals(ErrorCode.INVALID_REQUEST, assertFailsWith<DomainException> { queryService.findMine(1L, 0, 101) }.errorCode)
    }

    @Test
    fun 주문_결과별로_한번씩_기록된다() {
        place()
        assertEquals(listOf(PlaceOutcome.PAID), metrics.placed)

        paymentProcessor.result = PaymentResult.Failed(CancelReason.PAYMENT_FAILED)
        place(memberId = 2L)
        paymentProcessor.result = PaymentResult.Failed(CancelReason.PAYMENT_TIMEOUT)
        place(memberId = 3L)
        paymentProcessor.result = PaymentResult.Approved(77L)

        stockManager.remaining = 0
        assertFailsWith<DomainException> { place(memberId = 4L) }
        stockManager.remaining = 5

        assertFailsWith<DomainException> { place(quantity = 3, memberId = 5L) }

        dealReader.snapshot = DealSnapshot(10L, 5000, 2, ongoing = false, totalQuantity = 5)
        assertFailsWith<DomainException> { place(memberId = 6L) }

        assertFailsWith<DomainException> { service.place(PlaceOrderCommand(7L, 999L, 1)) }

        assertEquals(
            listOf(
                PlaceOutcome.PAID,
                PlaceOutcome.PAYMENT_FAILED,
                PlaceOutcome.PAYMENT_TIMEOUT,
                PlaceOutcome.SOLD_OUT,
                PlaceOutcome.LIMIT_EXCEEDED,
                PlaceOutcome.DEAL_NOT_ONGOING,
                PlaceOutcome.OTHER,
            ),
            metrics.placed,
        )
    }

    @Test
    fun 결제_승인_주문은_딜_총수량_등록과_함께_순판매량에_더해진다() {
        place(quantity = 2)

        assertEquals(5, metrics.totals[10L])
        assertEquals(2L, metrics.sold[10L])
    }

    @Test
    fun 결제_실패_주문은_순판매량에_반영되지_않는다() {
        paymentProcessor.result = PaymentResult.Failed(CancelReason.PAYMENT_FAILED)

        place(quantity = 2)

        assertTrue(metrics.sold.isEmpty())
        assertTrue(metrics.totals.isEmpty())
    }

    @Test
    fun 거절된_주문은_순판매량에_반영되지_않는다() {
        stockManager.remaining = 1

        assertFailsWith<DomainException> { place(quantity = 2) }

        assertTrue(metrics.sold.isEmpty())
    }

    @Test
    fun 결제완료_주문을_취소하면_순판매량이_줄어든다() {
        val placed = place(quantity = 2)

        service.cancel(1L, placed.orderId)

        assertEquals(0L, metrics.sold[10L])
    }

    @Test
    fun CREATED_주문_취소는_순판매량을_바꾸지_않는다() {
        stockManager.remaining = 3
        val id = existing(1L, 2, OrderStatus.CREATED)

        service.cancel(1L, id)

        assertTrue(metrics.sold.isEmpty())
    }
}
