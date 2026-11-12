package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.OrderItemStatus
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
    private val tx = InlineTransactionRunner()
    private val pointEarner = FakePointEarner()
    private val paymentService = OrderPaymentService(orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), tx, clock)
    private val shippingService = OrderShippingService(orderRepository, tx, clock, pointEarner)
    private val returnService = OrderReturnService(
        orderRepository, productReader, paymentGateway, stockManager, FakeBenefitReverter(stockManager.events), pointEarner, OrderProperties(), tx, clock,
    )
    private val cancelService = OrderCancelService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(stockManager.events), OrderProperties(), tx, clock,
    )
    private val reviewStateReader = FakeReviewStateReader()
    private val service = OrderQueryService(orderRepository, paymentGateway, productReader, OrderProperties(), reviewStateReader)

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
    fun 남은_결제_금액은_총액에서_환불액을_뺀_값이고_목록_만료_시각은_상세와_같다() {
        val order = place(10L, 20L)

        val before = service.getMine(memberId, order.orderId)
        assertEquals(before.totalAmount, before.remainingAmount)
        assertEquals(before.expiresAt, service.listMine(memberId, 0, 20).content.single().expiresAt)
        assertEquals(before.expiresAt, service.listForAdmin(null, null, null, 0, 20).content.single().expiresAt)
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

    /** 결제·발송·배송 완료까지 하고, [confirmSellerIds] 의 판매자 주문만 구매 확정한다. */
    private fun deliverAndConfirm(order: PlaceOrderResult, vararg confirmSellerIds: Long) {
        pay(order)
        orderRepository.findById(order.orderId)!!.sellerOrders.forEach {
            shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
            shippingService.deliver(it.sellerId, it.id!!)
        }
        confirmSellerIds.forEach { shippingService.confirmPurchase(memberId, order.orderId, sellerOrderIdOf(order, it)) }
    }

    private fun itemIdOf(order: PlaceOrderResult, skuId: Long) =
        orderRepository.findById(order.orderId)!!.allItems.first { it.skuId == skuId }.id!!

    @Test
    fun 구매_확정된_항목은_후기_상태를_reader에서_받는다() {
        val order = place(10L)
        deliverAndConfirm(order, 1L)
        val itemId = itemIdOf(order, 10L)
        reviewStateReader.states[itemId] = ItemReviewState("WRITABLE", null, now.plusDays(90))

        val item = service.getMine(memberId, order.orderId).sellerOrders.single().items.single()

        assertEquals(listOf(ReviewCandidate(itemId, now)), reviewStateReader.calls.single())
        assertEquals("WRITABLE", item.review?.status)
        assertNull(item.review?.reviewId)
        assertEquals(now.plusDays(90), item.review?.writableUntil)
    }

    @Test
    fun 확정_전_판매자_주문의_항목과_취소된_항목은_NOT_ELIGIBLE이고_reader에_묻지_않는다() {
        productReader.put(11L, sellerId = 1L, salePrice = 9_000)
        stockManager.stock[11L] = 50
        val order = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1), OrderItemCommand(11L, 1), OrderItemCommand(20L, 1)), 33_000, address),
        )
        pay(order)
        val confirmedItemId = itemIdOf(order, 10L)
        val canceledId = itemIdOf(order, 11L)
        val unconfirmedItemId = itemIdOf(order, 20L)
        // 판매자 1 의 항목 11 을 발송 전에 취소하고, 판매자 1 만 발송·배송 완료·확정한다(판매자 2 는 결제 완료 상태).
        paymentGateway.nextCancel = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.PARTIAL_CANCELED, 33_000, 24_000, listOf(PgCancelInfo(9_000, "사유"))))
        cancelService.cancelByBuyer(memberId, order.orderId, canceledId, "변심")
        shippingService.ship(1L, sellerOrderIdOf(order, 1L), "04", "T-1")
        shippingService.deliver(1L, sellerOrderIdOf(order, 1L))
        shippingService.confirmPurchase(memberId, order.orderId, sellerOrderIdOf(order, 1L))
        reviewStateReader.states[confirmedItemId] = ItemReviewState("WRITABLE", null, now.plusDays(90))

        val items = service.getMine(memberId, order.orderId).sellerOrders.flatMap { it.items }.associateBy { it.orderItemId }

        assertEquals("CANCELED", items.getValue(canceledId).status)
        assertEquals("NOT_ELIGIBLE", items.getValue(canceledId).review?.status)
        assertEquals("NOT_ELIGIBLE", items.getValue(unconfirmedItemId).review?.status)
        assertEquals("WRITABLE", items.getValue(confirmedItemId).review?.status)
        assertEquals(listOf(confirmedItemId), reviewStateReader.calls.single().map { it.orderItemId })
    }

    @Test
    fun 주문_한_건의_후기_상태는_한_번에_조회한다() {
        productReader.put(11L, sellerId = 1L, salePrice = 9_000)
        stockManager.stock[11L] = 50
        val order = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1), OrderItemCommand(11L, 1), OrderItemCommand(20L, 1)), 33_000, address),
        )
        deliverAndConfirm(order, 1L, 2L)
        val itemIds = listOf(itemIdOf(order, 10L), itemIdOf(order, 11L), itemIdOf(order, 20L))
        itemIds.forEach { reviewStateReader.states[it] = ItemReviewState("WRITABLE", null, now.plusDays(90)) }

        val detail = service.getMine(memberId, order.orderId)

        assertEquals(1, reviewStateReader.calls.size)
        assertEquals(itemIds.toSet(), reviewStateReader.calls.single().map { it.orderItemId }.toSet())
        assertEquals(listOf("WRITABLE", "WRITABLE", "WRITABLE"), detail.sellerOrders.flatMap { it.items }.map { it.review?.status })
        assertEquals(itemIds.toSet(), detail.sellerOrders.flatMap { it.items }.map { it.orderItemId }.toSet())
    }

    @Test
    fun 구매_확정_전_주문의_후기_상태는_NOT_ELIGIBLE이고_reader를_부르지_않는다() {
        val order = place(10L)
        pay(order)

        val detail = service.getMine(memberId, order.orderId)

        assertEquals("NOT_ELIGIBLE", detail.sellerOrders.single().items.single().review?.status)
        assertEquals(0, reviewStateReader.calls.size)
    }

    @Test
    fun 판매자와_관리자_주문_조회의_review는_null이다() {
        val order = place(10L)
        deliverAndConfirm(order, 1L)
        reviewStateReader.states[itemIdOf(order, 10L)] = ItemReviewState("WRITABLE", null, now.plusDays(90))

        val seller = service.getForSeller(1L, sellerOrderIdOf(order, 1L))
        val admin = service.getForAdmin(order.orderId)

        assertNull(seller.items.single().review)
        assertNull(admin.sellerOrders.single().items.single().review)
        assertEquals(0, reviewStateReader.calls.size)
    }

    @Test
    fun findConfirmedForSettlement는_CONFIRMED이고_confirmed_at_반열린_구간의_판매자_주문을_id_keyset으로_주고_항목별_sellerBorneReturnFee를_담는다() {
        productReader.put(11L, sellerId = 1L, salePrice = 9_000)
        stockManager.stock[11L] = 50
        val a = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1), OrderItemCommand(11L, 1), OrderItemCommand(20L, 1)), 33_000, address),
        )
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 33_000, 33_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(a.orderNo, "pay-${a.orderId}", 33_000))
        val b = place(10L)
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 12_000, 12_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(b.orderNo, "pay-${b.orderId}", 12_000))
        listOf(a, b).forEach { o ->
            orderRepository.findById(o.orderId)!!.sellerOrders.forEach {
                shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
                shippingService.deliver(it.sellerId, it.id!!)
            }
        }
        val seller1A = sellerOrderIdOf(a, 1L)
        val seller2A = sellerOrderIdOf(a, 2L)
        val seller1B = sellerOrderIdOf(b, 1L)
        // 판매자 1의 항목 11 불량 반품 완료(판매자 부담 3,000), 이후 판매자 주문 1·2(주문 A) 구매 확정. B 는 배송 완료 상태로 둔다.
        val item11 = orderRepository.findById(a.orderId)!!.allItems.first { it.skuId == 11L }.id!!
        val returnId = returnService.request(memberId, a.orderId, item11, RequestReturnCommand("DEFECTIVE", null)).returnId
        returnService.approveBySeller(1L, returnId)
        paymentGateway.nextCancel = PgOutcome.Done(
            PgPaymentInfo(PgPaymentStatus.PARTIAL_CANCELED, 33_000, 24_000, listOf(PgCancelInfo(9_000, "사유"))),
        )
        returnService.receiveBySeller(1L, returnId)
        shippingService.confirmPurchase(memberId, a.orderId, seller1A)
        shippingService.confirmPurchase(memberId, a.orderId, seller2A)

        val all = service.findConfirmedForSettlement(now, now.plusSeconds(1), 0, 10)

        assertEquals(listOf(seller1A, seller2A).sorted(), all.map { it.sellerOrderId })
        assertEquals(false, seller1B in all.map { it.sellerOrderId }) // 배송 완료 상태(CONFIRMED 아님)
        val first = all.first { it.sellerOrderId == seller1A }
        assertEquals(1L, first.sellerId)
        assertEquals(a.orderId, first.orderId)
        assertEquals(a.orderNo, first.orderNo)
        assertEquals(now, first.confirmedAt)
        assertEquals(3_000, first.shippingFee)
        assertEquals(0, first.buyerShippingFeeRefunded)
        assertEquals(
            mapOf("ORDERED" to 0L, "RETURNED" to 3_000L),
            first.items.associate { it.status to it.sellerBorneReturnFee },
        )
        assertEquals(OrderItemStatus.RETURNED.name, first.items.first { it.orderItemId == item11 }.status)
        assertEquals(9_000, first.items.first { it.orderItemId == item11 }.lineAmount)
        assertEquals(null, first.items.first { it.status == "ORDERED" }.productCouponIssuer)

        // keyset·limit
        assertEquals(listOf(all[1].sellerOrderId), service.findConfirmedForSettlement(now, now.plusSeconds(1), all[0].sellerOrderId, 10).map { it.sellerOrderId })
        assertEquals(listOf(all[0].sellerOrderId), service.findConfirmedForSettlement(now, now.plusSeconds(1), 0, 1).map { it.sellerOrderId })
        // 반열린 구간: to 는 포함하지 않고 from 은 포함한다
        assertEquals(emptyList(), service.findConfirmedForSettlement(now.minusDays(1), now, 0, 10))
        assertEquals(2, service.findConfirmedForSettlement(now, now.plusDays(1), 0, 10).size)
    }

    @Test
    fun 삭제된_상품의_주문_이미지는_null이다() {
        productReader.thumbnails[110L] = "http://files/110.png" // SKU 10 의 상품. SKU 20 의 상품(120)은 삭제돼 빠졌다
        val order = place(10L, 20L)
        pay(order)

        val detail = service.getMine(memberId, order.orderId)

        val byProduct = detail.sellerOrders.flatMap { it.items }.associate { it.productId to it.thumbnailUrl }
        assertEquals("http://files/110.png", byProduct[110L])
        assertNull(byProduct[120L])
        val summary = service.listMine(memberId, 0, 20).content.single()
        assertEquals("http://files/110.png", summary.thumbnailUrl)

        val deletedFirst = place(20L)
        assertNull(service.listMine(memberId, 0, 20).content.first { it.orderId == deletedFirst.orderId }.thumbnailUrl)
    }

    @Test
    fun 주문_목록_한_페이지의_이미지는_한_번에_조회한다() {
        productReader.thumbnails[110L] = "http://files/110.png"
        productReader.thumbnails[120L] = "http://files/120.png"
        pay(place(10L))
        pay(place(20L))
        pay(place(10L, 20L))

        productReader.thumbnailCalls = 0
        val page = service.listMine(memberId, 0, 20)

        assertEquals(3, page.content.size)
        assertEquals(1, productReader.thumbnailCalls)
        assertEquals(0, run { productReader.thumbnailCalls = 0; service.listMine(memberId + 1, 0, 20); productReader.thumbnailCalls })
    }
}
