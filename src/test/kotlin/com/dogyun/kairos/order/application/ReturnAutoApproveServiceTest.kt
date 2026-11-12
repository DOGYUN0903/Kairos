package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.order.domain.DecidedBy
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.ReturnStatus
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReturnAutoApproveServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private fun clockAt(t: LocalDateTime) = Clock.fixed(t.atZone(zone).toInstant(), zone)
    private val tx = InlineTransactionRunner()
    private val properties = OrderProperties()

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val pointEarner = FakePointEarner()
    private val clock = clockAt(now)
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), tx, clock,
    )
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, FakeBenefitApplier(), paymentService, properties, tx, clock, Random(7),
    )
    private val shippingService = OrderShippingService(orderRepository, tx, clock, pointEarner)
    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    private fun returnServiceAt(t: LocalDateTime) = OrderReturnService(
        orderRepository, productReader, paymentGateway, stockManager, FakeBenefitReverter(stockManager.events), pointEarner, properties, tx, clockAt(t),
    )

    private fun autoApproveAt(t: LocalDateTime) = ReturnAutoApproveService(orderRepository, returnServiceAt(t), properties, clockAt(t))

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        productReader.put(1L, sellerId = 1L, salePrice = 30_000)
        stockManager.stock[1L] = 10
    }

    /** 배송 완료 주문의 반품을 now 에 요청하고 (order, returnId) 를 돌려준다. */
    private fun requestedReturn(): Pair<Order, Long> {
        val placed = orderService.place(memberId, PlaceOrderCommand(listOf(OrderItemCommand(1L, 1)), 33_000, address, pointsToUse = 0))
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 33_000, 33_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", 33_000))
        val order = orderRepository.findById(placed.orderId)!!
        order.sellerOrders.forEach {
            shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
            shippingService.deliver(it.sellerId, it.id!!)
        }
        val itemId = orderRepository.findById(placed.orderId)!!.allItems.first().id!!
        val returnId = returnServiceAt(now).request(memberId, placed.orderId, itemId, RequestReturnCommand("CHANGE_OF_MIND", null)).returnId
        return order to returnId
    }

    private fun returnOf(order: Order, returnId: Long) =
        orderRepository.findById(order.id!!)!!.findReturn(returnId).second.returnRequest!!

    @Test
    fun 삼일_지난_REQUESTED는_SYSTEM이_승인하고_이일_이십삼시간은_그대로다() {
        val (order, returnId) = requestedReturn()
        val threshold = properties.returnAutoApproveAfter

        assertEquals(0, autoApproveAt(now.plus(threshold).minusHours(1)).approveStale())
        assertEquals(ReturnStatus.REQUESTED, returnOf(order, returnId).status)

        assertEquals(1, autoApproveAt(now.plus(threshold).plusMinutes(1)).approveStale())
        val approved = returnOf(order, returnId)
        assertEquals(ReturnStatus.APPROVED, approved.status)
        assertEquals(DecidedBy.SYSTEM, approved.decidedBy)
    }

    @Test
    fun 이미_판매자가_승인한_건은_false다() {
        val (order, returnId) = requestedReturn()
        val later = now.plus(Duration.ofDays(4))
        returnServiceAt(later).approveBySeller(1L, returnId)

        assertEquals(0, autoApproveAt(later).approveStale())
        assertEquals(DecidedBy.SELLER, returnOf(order, returnId).decidedBy)
    }
}
