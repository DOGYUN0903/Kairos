package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderPaymentServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val cartItemRemover = FakeCartItemRemover()
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, OrderProperties(), clock, Random(7),
    )
    private val service = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, cartItemRemover, InlineTransactionRunner(), clock,
    )

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        productReader.put(10L, sellerId = 1L, salePrice = 9_000)
        stockManager.stock[10L] = 5
    }

    /** 9000 * 2 + 배송비 3000 = 21000 짜리 주문 하나. */
    private fun place(): PlaceOrderResult =
        orderService.place(memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 2)), 21_000, address))

    private fun pgDone(amount: Long, status: PgPaymentStatus = PgPaymentStatus.DONE) =
        PgOutcome.Done(PgPaymentInfo(status, amount, amount, emptyList()))

    private fun confirm(order: PlaceOrderResult, key: String = "pay-1", amount: Long = order.totalAmount, member: Long = memberId) =
        service.confirm(member, ConfirmPaymentCommand(order.orderNo, key, amount))

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 승인되면_주문_PAID_결제_APPROVED_판매량_증가_장바구니_제거다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(21_000)

        val result = confirm(order)

        assertEquals(ConfirmPaymentResult(order.orderId, order.orderNo, "PAID", "APPROVED"), result)
        val saved = orderRepository.findById(order.orderId)!!
        assertEquals(OrderStatus.PAID, saved.status)
        assertEquals(now, saved.paidAt)
        assertEquals(setOf(SellerOrderStatus.PAID), saved.sellerOrders.map { it.status }.toSet())
        assertEquals("APPROVED", paymentGateway.get(order.orderId).status)
        assertEquals(mapOf(110L to 2), stockManager.sales)
        assertEquals(listOf(memberId to setOf(10L)), cartItemRemover.removed)
        assertEquals(listOf(Triple("pay-1", order.orderNo, 21_000L)), paymentGateway.confirmCalls)
    }

    @Test
    fun 금액이_다르면_PAYMENT_AMOUNT_MISMATCH이고_PG를_호출하지_않는다() {
        val order = place()

        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH) { confirm(order, amount = 20_000) }

        assertTrue(paymentGateway.confirmCalls.isEmpty())
        assertEquals("READY", paymentGateway.get(order.orderId).status)
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(order.orderId)!!.status)
    }

    @Test
    fun PG가_거절하면_주문_CANCELED_재고_복구_결제_FAILED_402() {
        val order = place()
        paymentGateway.nextConfirm = PgOutcome.Failed("REJECT_CARD_COMPANY", "카드사가 승인을 거절했습니다.")

        assertError(ErrorCode.PAYMENT_REJECTED) { confirm(order, "fail-1") }

        val saved = orderRepository.findById(order.orderId)!!
        assertEquals(OrderStatus.CANCELED, saved.status)
        assertEquals(setOf(OrderItemStatus.CANCELED), saved.allItems.map { it.status }.toSet())
        assertEquals(5, stockManager.stock[10L])
        assertEquals("FAILED", paymentGateway.get(order.orderId).status)
        assertEquals("REJECT_CARD_COMPANY", paymentGateway.get(order.orderId).failReason)
        assertTrue(stockManager.sales.isEmpty())
        assertTrue(cartItemRemover.removed.isEmpty())
    }

    @Test
    fun PG_결과가_불확실하면_결제_UNKNOWN이고_주문은_PENDING_PAYMENT다() {
        val order = place()
        paymentGateway.nextConfirm = PgOutcome.Unknown

        val result = confirm(order, "unknown-1")

        assertEquals(ConfirmPaymentResult(order.orderId, order.orderNo, "PENDING_PAYMENT", "UNKNOWN"), result)
        assertEquals("UNKNOWN", paymentGateway.get(order.orderId).status)
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(order.orderId)!!.status)
        assertEquals(3, stockManager.stock[10L])
    }

    @Test
    fun 이미_APPROVED인_주문을_같은_키로_다시_승인하면_PG_없이_200이다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(21_000)
        confirm(order)

        val again = confirm(order)

        assertEquals(ConfirmPaymentResult(order.orderId, order.orderNo, "PAID", "APPROVED"), again)
        assertEquals(1, paymentGateway.confirmCalls.size)
        assertEquals(mapOf(110L to 2), stockManager.sales)
    }

    @Test
    fun UNKNOWN_결제를_같은_키로_다시_승인하면_PG_없이_200_UNKNOWN이다() {
        val order = place()
        paymentGateway.nextConfirm = PgOutcome.Unknown
        confirm(order, "unknown-1")

        val again = confirm(order, "unknown-1")

        assertEquals(ConfirmPaymentResult(order.orderId, order.orderNo, "PENDING_PAYMENT", "UNKNOWN"), again)
        assertEquals(1, paymentGateway.confirmCalls.size)
    }

    @Test
    fun 승인이_진행_중이면_PAYMENT_IN_PROGRESS이고_PG를_호출하지_않는다() {
        val order = place()
        paymentGateway.start(order.orderId, "pay-1")

        assertError(ErrorCode.PAYMENT_IN_PROGRESS) { confirm(order, "pay-1") }
        assertError(ErrorCode.PAYMENT_IN_PROGRESS) { confirm(order, "pay-2") }

        assertTrue(paymentGateway.confirmCalls.isEmpty())
    }

    @Test
    fun 이미_결제된_주문을_다른_키로_승인하면_ORDER_INVALID_STATE_TRANSITION이다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(21_000)
        confirm(order)

        assertError(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { confirm(order, "pay-2") }

        assertEquals(1, paymentGateway.confirmCalls.size)
    }

    @Test
    fun 남의_주문번호_승인은_404이고_PG를_호출하지_않는다() {
        val order = place()

        assertError(ErrorCode.ORDER_NOT_FOUND) { confirm(order, member = 99L) }
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.confirm(memberId, ConfirmPaymentCommand("K-없는-주문", "pay-1", 1)) }

        assertTrue(paymentGateway.confirmCalls.isEmpty())
        assertEquals("READY", paymentGateway.get(order.orderId).status)
    }

    @Test
    fun 주문이_이미_취소된_뒤_승인_결과가_오면_PG_전액_취소를_호출하고_결제는_CANCELED_전액_환불이다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(21_000)
        paymentGateway.nextCancel = pgDone(21_000, PgPaymentStatus.CANCELED)
        // PG 응답을 기다리는 사이 다른 흐름이 주문을 닫았다.
        paymentGateway.duringConfirm = {
            val closed = orderRepository.findById(order.orderId)!!
            closed.cancelForPaymentFailure()
            orderRepository.save(closed)
        }

        assertError(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { confirm(order) }

        assertEquals(
            listOf(CancelCall("pay-1", 21_000, "order-closed:${order.orderId}", "void-${order.orderId}-21000")),
            paymentGateway.cancelCalls,
        )
        val payment = paymentGateway.get(order.orderId)
        assertEquals("CANCELED", payment.status)
        assertEquals(21_000L, payment.refundedAmount)
        assertEquals(listOf(order.orderId), paymentGateway.voidedOrderIds)
        assertTrue(stockManager.sales.isEmpty())
    }

    @Test
    fun 주문이_닫힌_뒤에_거절이_와도_PG_취소는_부르지_않는다() {
        val order = place()
        paymentGateway.nextConfirm = PgOutcome.Failed("REJECT_CARD_COMPANY", "거절")
        paymentGateway.duringConfirm = {
            val closed = orderRepository.findById(order.orderId)!!
            closed.cancelForPaymentFailure()
            orderRepository.save(closed)
        }

        assertError(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { confirm(order) }

        assertTrue(paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun PG_금액이_주문과_다르면_void_후_거절한다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(1_000)
        paymentGateway.nextCancel = pgDone(1_000, PgPaymentStatus.CANCELED)

        assertError(ErrorCode.PAYMENT_REJECTED) { confirm(order) }

        assertEquals(
            listOf(CancelCall("pay-1", 1_000, "amount-mismatch:${order.orderId}", "void-${order.orderId}-1000")),
            paymentGateway.cancelCalls,
        )
        assertEquals("FAILED", paymentGateway.get(order.orderId).status)
        assertEquals(OrderStatus.CANCELED, orderRepository.findById(order.orderId)!!.status)
        assertEquals(5, stockManager.stock[10L])
    }

    private fun voidOutcomes() = listOf(
        PgOutcome.Unknown,
        PgOutcome.Failed("REJECT_CANCEL", "취소가 거절되었습니다."),
        pgDone(21_000, PgPaymentStatus.DONE),
    )

    @Test
    fun PG_금액이_다르고_void가_확인되지_않으면_402이지만_결제는_UNKNOWN으로_남는다() {
        stockManager.stock[10L] = 50
        voidOutcomes().forEach { cancel ->
            val order = place()
            paymentGateway.nextConfirm = pgDone(1_000)
            paymentGateway.nextCancel = cancel

            assertError(ErrorCode.PAYMENT_REJECTED) { confirm(order, "pay-${order.orderId}") }

            assertEquals("UNKNOWN", paymentGateway.get(order.orderId).status, "$cancel")
            assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(order.orderId)!!.status)
        }
    }

    @Test
    fun 주문이_닫힌_뒤_void가_확인되지_않으면_409이지만_결제는_UNKNOWN으로_남는다() {
        stockManager.stock[10L] = 50
        voidOutcomes().forEach { cancel ->
            val order = place()
            paymentGateway.nextConfirm = pgDone(21_000)
            paymentGateway.nextCancel = cancel
            paymentGateway.duringConfirm = {
                val closed = orderRepository.findById(order.orderId)!!
                closed.cancelForPaymentFailure()
                orderRepository.save(closed)
            }

            assertError(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { confirm(order, "pay-${order.orderId}") }

            val payment = paymentGateway.get(order.orderId)
            assertEquals("UNKNOWN", payment.status, "$cancel")
            assertEquals(0L, payment.refundedAmount)
            assertTrue(order.orderId !in paymentGateway.voidedOrderIds)
        }
    }

    @Test
    fun judge는_ABORTED_EXPIRED_CANCELED_Failed를_REJECT_OTHER_Unknown을_HOLD로_본다() {
        assertEquals(PgVerdict.APPROVE, judge(pgDone(100), 100))
        assertEquals(PgVerdict.VOID_THEN_REJECT, judge(pgDone(100), 200))
        listOf(PgPaymentStatus.ABORTED, PgPaymentStatus.EXPIRED, PgPaymentStatus.CANCELED).forEach {
            assertEquals(PgVerdict.REJECT, judge(pgDone(100, it), 100), "$it")
        }
        assertEquals(PgVerdict.REJECT, judge(PgOutcome.Failed("X", "y"), 100))
        listOf(PgPaymentStatus.OTHER, PgPaymentStatus.PARTIAL_CANCELED).forEach {
            assertEquals(PgVerdict.HOLD, judge(pgDone(100, it), 100), "$it")
        }
        assertEquals(PgVerdict.HOLD, judge(PgOutcome.Unknown, 100))
    }

    @Test
    fun 결제가_이미_끝났으면_finishApproval은_ALREADY_SETTLED_no_op이다() {
        val order = place()
        paymentGateway.nextConfirm = pgDone(21_000)
        confirm(order)

        assertEquals(
            ApprovalFinish.ALREADY_SETTLED,
            service.finishApproval(order.orderId, "pay-1", PgVerdict.REJECT, "late"),
        )

        assertEquals(OrderStatus.PAID, orderRepository.findById(order.orderId)!!.status)
        assertEquals("APPROVED", paymentGateway.get(order.orderId).status)
        assertEquals(3, stockManager.stock[10L])
    }

    @Test
    fun paymentKey가_다르면_finishApproval은_ALREADY_SETTLED다() {
        val order = place()
        paymentGateway.start(order.orderId, "pay-1")

        assertEquals(
            ApprovalFinish.ALREADY_SETTLED,
            service.finishApproval(order.orderId, "other", PgVerdict.APPROVE, ""),
        )

        assertEquals("IN_PROGRESS", paymentGateway.get(order.orderId).status)
    }
}
