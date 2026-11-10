package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.CancelActor
import com.dogyun.kairos.order.domain.Order
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

class OrderShippingServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, OrderProperties(), clock, Random(7),
    )
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeCartItemRemover(), InlineTransactionRunner(), clock,
    )
    private val service = OrderShippingService(orderRepository, InlineTransactionRunner(), clock)

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        sellerReader.put(2L)
        productReader.put(10L, sellerId = 1L, salePrice = 30_000)
        productReader.put(20L, sellerId = 2L, salePrice = 30_000)
        stockManager.stock[10L] = 10
        stockManager.stock[20L] = 10
    }

    /** 판매자 1·2 각각 30,000 (무료배송) 주문을 PAID 로 만든다. */
    private fun paidOrder(): Order {
        val placed = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1), OrderItemCommand(20L, 1)), 60_000, address),
        )
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 60_000, 60_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", 60_000))
        return orderRepository.findById(placed.orderId)!!
    }

    private fun Order.soId(sellerId: Long) = sellerOrders.first { it.sellerId == sellerId }.id!!
    private fun reload(order: Order) = orderRepository.findById(order.id!!)!!

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 배송_시작하면_SHIPPING이고_택배사와_송장이_저장된다() {
        val order = paidOrder()
        val result = service.ship(1L, order.soId(1L), "04", "123456")

        val so = reload(order).findSellerOrder(order.soId(1L))
        assertEquals(SellerOrderStatus.SHIPPING, so.status)
        assertEquals("04", so.carrierCode)
        assertEquals("123456", so.trackingNumber)
        assertEquals(now, so.shippedAt)
        assertEquals(SellerOrderStatus.SHIPPING, result.sellerOrderStatus)
    }

    @Test
    fun 같은_송장_재요청은_200_다른_송장은_409() {
        val order = paidOrder()
        val soId = order.soId(1L)
        service.ship(1L, soId, "04", "123456")
        service.ship(1L, soId, "04", "123456")
        assertError(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { service.ship(1L, soId, "04", "999999") }
    }

    @Test
    fun 취소_확인_중인_항목이_있으면_배송_시작은_409다() {
        val order = paidOrder()
        val itemId = order.sellerOrders.first { it.sellerId == 1L }.items[0].id!!
        val plan = order.planCancel(itemId, CancelActor.SELLER, OrderProperties().shippingPolicy())
        order.requestCancel(itemId, CancelActor.SELLER, "사유", plan)
        orderRepository.save(order)

        assertError(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { service.ship(1L, order.soId(1L), "04", "123456") }
        assertEquals(OrderItemStatus.CANCEL_REQUESTED, reload(order).findItem(itemId).status)
    }

    @Test
    fun DELIVERED_전_구매_확정은_409() {
        val order = paidOrder()
        val soId = order.soId(1L)
        assertError(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { service.confirmPurchase(memberId, order.id!!, soId) }
        service.ship(1L, soId, "04", "123456")
        assertError(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { service.confirmPurchase(memberId, order.id!!, soId) }
    }

    @Test
    fun 마지막_판매자_주문을_확정하면_주문이_COMPLETED다() {
        val order = paidOrder()
        for (sellerId in listOf(1L, 2L)) {
            val soId = order.soId(sellerId)
            service.ship(sellerId, soId, "04", "T$sellerId")
            service.deliver(sellerId, soId)
        }
        service.confirmPurchase(memberId, order.id!!, order.soId(1L))
        assertEquals(OrderStatus.PAID, reload(order).status)
        service.confirmPurchase(memberId, order.id, order.soId(1L))
        service.confirmPurchase(memberId, order.id, order.soId(2L))

        val done = reload(order)
        assertEquals(OrderStatus.COMPLETED, done.status)
        assertEquals(SellerOrderStatus.CONFIRMED, done.findSellerOrder(order.soId(2L)).status)
        assertEquals(now, done.findSellerOrder(order.soId(2L)).confirmedAt)
    }

    @Test
    fun 다른_판매자의_판매자_주문은_SELLER_ORDER_NOT_FOUND() {
        val order = paidOrder()
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.ship(2L, order.soId(1L), "04", "123456") }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.deliver(2L, order.soId(1L)) }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.ship(1L, 99_999L, "04", "123456") }
    }

    @Test
    fun 다른_회원의_구매_확정은_ORDER_NOT_FOUND() {
        val order = paidOrder()
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.confirmPurchase(memberId + 1, order.id!!, order.soId(1L)) }
    }

    @Test
    fun 시스템_배송_완료는_SHIPPING이_아니면_아무것도_하지_않고_자동_확정은_DELIVERED만_확정한다() {
        val order = paidOrder()
        val soId = order.soId(1L)
        service.deliverBySystem(soId)
        service.autoConfirm(soId)
        assertEquals(SellerOrderStatus.PAID, reload(order).findSellerOrder(soId).status)

        service.ship(1L, soId, "04", "123456")
        service.deliverBySystem(soId)
        assertEquals(SellerOrderStatus.DELIVERED, reload(order).findSellerOrder(soId).status)
        service.autoConfirm(soId)
        assertEquals(SellerOrderStatus.CONFIRMED, reload(order).findSellerOrder(soId).status)
    }
}
