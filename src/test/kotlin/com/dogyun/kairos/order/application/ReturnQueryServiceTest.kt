package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.PointUsePolicy
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
    private val benefitApplier = FakeBenefitApplier()
    private val pointEarner = FakePointEarner()
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), tx, clock,
    )
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, benefitApplier, paymentService, properties, tx, clock, Random(7),
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

    private fun deliveredOrder(skuId: Long, points: Long = 0): Order {
        val total = 33_000 - points
        val placed = orderService.place(memberId, PlaceOrderCommand(listOf(OrderItemCommand(skuId, 1)), total, address, pointsToUse = points))
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, total, total, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", total))
        val order = orderRepository.findById(placed.orderId)!!
        order.sellerOrders.forEach {
            shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
            shippingService.deliver(it.sellerId, it.id!!)
        }
        return orderRepository.findById(placed.orderId)!!
    }

    private fun requestReturn(skuId: Long, points: Long = 0): ReturnRequestResult {
        val order = deliveredOrder(skuId, points)
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

    private fun assertDisplay(r: ReturnRequestResult, requested: ReturnRequestResult) {
        assertEquals(requested.orderNo, r.orderNo)
        assertEquals(requested.sellerName, r.sellerName)
        assertEquals("판매자1", r.sellerName)
        assertEquals(requested.paidAmount, r.paidAmount)
        assertEquals("http://files/1.png", r.thumbnailUrl)
        assertEquals(requested.optionDisplayName, r.optionDisplayName)
    }

    @Test
    fun paidAmount는_포인트를_뺀_현금_결제_몫이다() {
        benefitApplier.balance = 10_000
        benefitApplier.policy = PointUsePolicy(minUsableBalance = 0, useUnit = 10, maxUseRateBp = 10_000)
        val requested = requestReturn(1L, points = 10_000)
        val item = orderRepository.findById(requested.orderId)!!.allItems.first()

        assertEquals(30_000, item.lineAmount)
        assertEquals(20_000, requested.paidAmount)
        assertEquals(20_000, service.getMine(memberId, requested.returnId).paidAmount)
        assertEquals(20_000, service.listForAdmin(null, null, null, 0, 20).content.single().paidAmount)
    }

    @Test
    fun 상태를_바꾸는_응답에도_표시용_필드가_있고_자동_승인_시각은_null이다() {
        val requested = requestReturn(1L)
        val withdrawn = returnService.withdraw(memberId, requested.returnId)
        assertDisplay(withdrawn, requested)
        assertNull(withdrawn.autoApproveAt)

        val second = requestReturn(2L)
        val approved = returnService.approveBySeller(1L, second.returnId)
        assertEquals(second.orderNo, approved.orderNo)
        assertEquals(second.sellerName, approved.sellerName)
        assertEquals(second.paidAmount, approved.paidAmount)
        assertEquals("http://files/2.png", approved.thumbnailUrl)
        assertNull(approved.autoApproveAt)

        paymentGateway.nextCancel = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.PARTIAL_CANCELED, 33_000, 6_000, listOf(PgCancelInfo(27_000, "사유"))))
        val received = returnService.receiveBySeller(1L, second.returnId)
        assertEquals(second.orderNo, received.orderNo)
        assertEquals(second.sellerName, received.sellerName)
        assertEquals(second.paidAmount, received.paidAmount)
        assertEquals("http://files/2.png", received.thumbnailUrl)
        assertNull(received.autoApproveAt)

        val third = requestReturn(3L)
        val rejected = returnService.rejectBySeller(1L, third.returnId, "사유")
        assertEquals(third.orderNo, rejected.orderNo)
        assertEquals("판매자1", rejected.sellerName)
        assertEquals(third.paidAmount, rejected.paidAmount)
        assertEquals("http://files/3.png", rejected.thumbnailUrl)
        assertNull(rejected.autoApproveAt)

        val byAdmin = returnService.approveByAdmin(third.returnId)
        assertEquals(third.orderNo, byAdmin.orderNo)
        assertEquals("판매자1", byAdmin.sellerName)
        assertEquals(third.paidAmount, byAdmin.paidAmount)
        assertEquals("http://files/3.png", byAdmin.thumbnailUrl)
    }

    @Test
    fun 철회_뒤_재요청하면_자동_승인_시각은_새_요청_시각에_3일을_더한_값이다() {
        val order = deliveredOrder(1L)
        val itemId = order.allItems.first().id!!
        val first = returnService.request(memberId, order.id!!, itemId, RequestReturnCommand("CHANGE_OF_MIND", null))
        assertNull(returnService.withdraw(memberId, first.returnId).autoApproveAt)
        assertNull(service.getMine(memberId, first.returnId).autoApproveAt)

        val later = now.plusDays(1)
        val again = OrderReturnService(
            orderRepository, productReader, paymentGateway, stockManager, FakeBenefitReverter(stockManager.events), pointEarner, properties, tx,
            Clock.fixed(later.atZone(zone).toInstant(), zone),
        ).request(memberId, order.id!!, itemId, RequestReturnCommand("CHANGE_OF_MIND", null))

        assertEquals(later, again.requestedAt)
        assertEquals(later.plusDays(3), again.autoApproveAt)
    }

    @Test
    fun 반품_응답에_구매자가_적은_상세_내용이_있다() {
        val order = deliveredOrder(1L)
        val text = "박스가 찢어져 왔어요.\n두 번째 줄"
        val requested = returnService.request(memberId, order.id!!, order.allItems.first().id!!, RequestReturnCommand("DEFECTIVE", text))

        assertEquals(text, requested.detail)
        assertEquals(text, service.getMine(memberId, requested.returnId).detail)
        assertEquals(text, service.getForSeller(1L, requested.returnId).detail)
        assertEquals(text, service.listForSeller(1L, null, 0, 20).content.single().detail)
        assertEquals(text, service.listMine(memberId, 0, 20).content.single().detail)
        assertEquals(text, service.getForAdmin(requested.returnId).detail)
        assertEquals(text, returnService.approveBySeller(1L, requested.returnId).detail)
    }

    @Test
    fun 상세_내용_없이_요청하면_detail은_null이다() {
        val requested = requestReturn(1L)

        assertNull(requested.detail)
        assertNull(service.getMine(memberId, requested.returnId).detail)
        assertNull(service.getForSeller(1L, requested.returnId).detail)
        assertNull(service.getForAdmin(requested.returnId).detail)
    }

    @Test
    fun 철회_뒤_다른_상세_내용으로_다시_요청하면_새_내용이_보인다() {
        val order = deliveredOrder(1L)
        val itemId = order.allItems.first().id!!
        val first = returnService.request(memberId, order.id!!, itemId, RequestReturnCommand("CHANGE_OF_MIND", "처음 내용"))
        assertEquals("처음 내용", first.detail)
        returnService.withdraw(memberId, first.returnId)

        val again = returnService.request(memberId, order.id!!, itemId, RequestReturnCommand("DEFECTIVE", "새 내용"))

        assertEquals(first.returnId, again.returnId)
        assertEquals("새 내용", again.detail)
        assertEquals("새 내용", service.getMine(memberId, first.returnId).detail)
        assertEquals("새 내용", service.getForSeller(1L, first.returnId).detail)
        assertEquals("새 내용", service.getForAdmin(first.returnId).detail)
    }
}
