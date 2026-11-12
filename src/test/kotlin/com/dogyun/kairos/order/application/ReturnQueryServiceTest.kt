package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.order.domain.Order
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReturnQueryServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val tx = InlineTransactionRunner()
    private val properties = OrderProperties()

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val pointEarner = FakePointEarner()
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), tx, clock,
    )
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, FakeBenefitApplier(), paymentService, properties, tx, clock, Random(7),
    )
    private val shippingService = OrderShippingService(orderRepository, tx, clock, pointEarner)
    private val returnService = OrderReturnService(
        orderRepository, productReader, paymentGateway, stockManager, FakeBenefitReverter(stockManager.events), pointEarner, properties, tx, clock,
    )
    private val service = ReturnQueryService(orderRepository, productReader, properties, clock)

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        for (sku in 1L..3L) {
            productReader.put(sku, sellerId = 1L, salePrice = 30_000)
            stockManager.stock[sku] = 10
            productReader.thumbnails[100L + sku] = "http://files/$sku.png"
        }
    }

    private fun deliveredOrder(skuId: Long): Order {
        val placed = orderService.place(memberId, PlaceOrderCommand(listOf(OrderItemCommand(skuId, 1)), 33_000, address))
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 33_000, 33_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", 33_000))
        val order = orderRepository.findById(placed.orderId)!!
        order.sellerOrders.forEach {
            shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
            shippingService.deliver(it.sellerId, it.id!!)
        }
        return orderRepository.findById(placed.orderId)!!
    }

    private fun requestReturn(skuId: Long): ReturnRequestResult {
        val order = deliveredOrder(skuId)
        return returnService.request(memberId, order.id!!, order.allItems.first().id!!, RequestReturnCommand("CHANGE_OF_MIND", null))
    }

    @Test
    fun 반품_결과에_표시용_필드가_채워진다() {
        val requested = requestReturn(1L)
        val order = orderRepository.findById(requested.orderId)!!
        val item = order.allItems.first()

        assertEquals("http://files/1.png", requested.thumbnailUrl)
        assertEquals(item.optionDisplayName, requested.optionDisplayName)
        assertEquals(order.orderNo, requested.orderNo)
        assertEquals(order.sellerOrders.first().sellerName, requested.sellerName)
        assertEquals(item.paidAmount, requested.paidAmount)
        assertEquals(requested, service.getMine(memberId, requested.returnId))
        assertEquals(requested, service.listMine(memberId, 0, 20).content.single())
    }

    @Test
    fun 자동_승인_예정_시각은_요청_상태에서만_있고_요청_시각에_3일을_더한_값이다() {
        val requested = requestReturn(1L)
        assertEquals(now.plusDays(3), requested.autoApproveAt)
        assertEquals(requested.requestedAt.plusDays(3), service.getForSeller(1L, requested.returnId).autoApproveAt)

        val approved = returnService.approveBySeller(1L, requested.returnId)
        assertNull(approved.autoApproveAt)
        assertNull(service.getForAdmin(requested.returnId).autoApproveAt)
        assertNull(service.listForSeller(1L, null, 0, 20).content.single().autoApproveAt)
    }

    @Test
    fun 삭제된_상품의_반품_이미지는_null이다() {
        val requested = requestReturn(1L)
        productReader.thumbnails.remove(101L)

        assertNull(service.getMine(memberId, requested.returnId).thumbnailUrl)
        assertNull(service.listMine(memberId, 0, 20).content.single().thumbnailUrl)
    }

    @Test
    fun 반품_목록_한_페이지의_이미지는_한_번에_조회한다() {
        listOf(1L, 2L, 3L).forEach { requestReturn(it) }
        productReader.thumbnailCalls = 0

        val page = service.listMine(memberId, 0, 20)

        assertEquals(3, page.content.size)
        assertEquals(setOf("http://files/1.png", "http://files/2.png", "http://files/3.png"), page.content.map { it.thumbnailUrl }.toSet())
        assertEquals(1, productReader.thumbnailCalls)
    }
}
