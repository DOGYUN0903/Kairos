package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderQueryServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, FakeBenefitApplier(),
        OrderPaymentService(orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), InlineTransactionRunner(), clock),
        OrderProperties(), InlineTransactionRunner(), clock, Random(7),
    )
    private val service = OrderQueryService(orderRepository, paymentGateway, OrderProperties())

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        sellerReader.put(2L)
        productReader.put(10L, sellerId = 1L, salePrice = 9_000)
        productReader.put(20L, sellerId = 2L, salePrice = 9_000)
        stockManager.stock[10L] = 50
        stockManager.stock[20L] = 50
    }

    private fun place(vararg skuIds: Long, member: Long = memberId): PlaceOrderResult =
        orderService.place(
            member,
            PlaceOrderCommand(
                skuIds.map { OrderItemCommand(it, 1) },
                // 9000 원 상품 n 개 + 판매자(SKU 하나당 한 명)마다 배송비 3000(5만원 미만)
                9_000L * skuIds.size + 3_000L * skuIds.toSet().size,
                address,
            ),
        )

    private fun pay(order: PlaceOrderResult) {
        val o = orderRepository.findById(order.orderId)!!
        o.markPaid(now)
        orderRepository.save(o)
        paymentGateway.start(order.orderId, "key-${order.orderId}")
        paymentGateway.approve(order.orderId)
    }

    private fun sellerOrderIdOf(order: PlaceOrderResult, sellerId: Long): Long =
        orderRepository.findById(order.orderId)!!.sellerOrders.first { it.sellerId == sellerId }.id!!

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 판매자_상세는_배송지와_자기_항목만_담는다() {
        val order = place(10L, 20L)
        pay(order)

        val detail = service.getForSeller(1L, sellerOrderIdOf(order, 1L))

        assertEquals(order.orderNo, detail.orderNo)
        assertEquals("홍길동", detail.shippingAddress.receiverName)
        assertEquals("서울 강남구 테헤란로 1", detail.shippingAddress.address1)
        assertEquals(listOf(10L), detail.items.map { it.skuId })
        assertEquals("PAID", detail.status)
    }

    @Test
    fun 판매자_목록은_결제_전_판매자_주문을_빼고_상태_필터를_건다() {
        val paid = place(10L)
        pay(paid)
        val shipped = place(10L, 20L)
        pay(shipped)
        orderRepository.findBySellerOrderId(sellerOrderIdOf(shipped, 1L))!!.also {
            it.ship(sellerOrderIdOf(shipped, 1L), "04", "1234567890", now)
            orderRepository.save(it)
        }
        place(10L) // 결제 전

        val all = service.listForSeller(1L, null, 0, 20)
        assertEquals(2, all.totalElements)
        assertEquals(listOf(sellerOrderIdOf(shipped, 1L), sellerOrderIdOf(paid, 1L)), all.content.map { it.sellerOrderId })
        assertEquals(1, all.content.first().itemCount)
        assertEquals("티셔츠10", all.content.first().firstProductName)

        val onlyShipping = service.listForSeller(1L, SellerOrderStatus.SHIPPING, 0, 20)
        assertEquals(listOf(sellerOrderIdOf(shipped, 1L)), onlyShipping.content.map { it.sellerOrderId })
        assertEquals(0, service.listForSeller(1L, SellerOrderStatus.PENDING_PAYMENT, 0, 20).totalElements)
    }

    @Test
    fun 남의_판매자_주문과_결제_전_판매자_주문_상세는_SELLER_ORDER_NOT_FOUND() {
        val paid = place(10L)
        pay(paid)
        val pending = place(10L)

        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.getForSeller(2L, sellerOrderIdOf(paid, 1L)) }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.getForSeller(1L, sellerOrderIdOf(pending, 1L)) }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.getForSeller(1L, 99_999L) }
    }

    @Test
    fun 남의_주문_상세는_ORDER_NOT_FOUND() {
        val order = place(10L)

        assertError(ErrorCode.ORDER_NOT_FOUND) { service.getMine(memberId + 1, order.orderId) }
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.getMine(memberId, 99_999L) }
    }

    @Test
    fun 내_주문_상세는_판매자별_묶음과_결제_정보를_담는다() {
        val order = place(10L, 20L)
        pay(order)

        val detail = service.getMine(memberId, order.orderId)

        assertEquals("PAID", detail.status)
        assertEquals(listOf(1L, 2L), detail.sellerOrders.map { it.sellerId })
        assertEquals(18_000L + 6_000L, detail.totalAmount)
        assertEquals("APPROVED", detail.payment?.status)
        assertEquals("key-${order.orderId}", detail.payment?.paymentKey)
    }

    @Test
    fun 결제_정보가_없으면_payment는_null이다() {
        val order = place(10L)
        paymentGateway.payments.clear()

        assertNull(service.getMine(memberId, order.orderId).payment)
    }

    @Test
    fun 내_목록은_내_주문만_최신순으로_요약한다() {
        val first = place(10L)
        val second = place(10L, 20L)
        place(10L, member = memberId + 1)

        val page = service.listMine(memberId, 0, 20)

        assertEquals(listOf(second.orderId, first.orderId), page.content.map { it.orderId })
        assertEquals(2, page.content.first().itemCount)
        assertEquals("티셔츠10", page.content.first().firstProductName)
        assertEquals(second.totalAmount, page.content.first().totalAmount)
    }

    @Test
    fun 관리자는_주문번호_회원_상태로_거르고_상세를_본다() {
        val a = place(10L)
        pay(a)
        val b = place(10L, member = memberId + 1)

        assertEquals(listOf(b.orderId), service.listForAdmin(null, memberId + 1, null, 0, 20).content.map { it.orderId })
        assertEquals(listOf(a.orderId), service.listForAdmin(null, null, a.orderNo, 0, 20).content.map { it.orderId })
        assertEquals(
            listOf(a.orderId),
            service.listForAdmin(com.dogyun.kairos.order.domain.OrderStatus.PAID, null, null, 0, 20).content.map { it.orderId },
        )
        assertEquals(b.orderNo, service.getForAdmin(b.orderId).orderNo)
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.getForAdmin(99_999L) }
    }

    @Test
    fun 페이지_범위를_벗어나면_INVALID_REQUEST() {
        assertError(ErrorCode.INVALID_REQUEST) { service.listMine(memberId, -1, 20) }
        assertError(ErrorCode.INVALID_REQUEST) { service.listMine(memberId, 0, 101) }
        assertError(ErrorCode.INVALID_REQUEST) { service.listForSeller(1L, null, 0, 0) }
        assertError(ErrorCode.INVALID_REQUEST) { service.listForAdmin(null, null, null, 0, 101) }
    }
}
